(ns eulalie.dynamo-test
  (:require [eulalie.dynamo :as dynamo]
            [eulalie.test-util :refer :all]
            [clojure.core.async :as async]
            [cemerick.url :refer [url]]
            [eulalie :refer :all]
            [eulalie.util :refer :all]
            [clojure.test :refer :all]))

(defn issue [target content & [req-overrides]]
  (let [req (merge
             {:service :dynamo
              :target  target
              :max-retries 0
              :body content
              :creds creds}
             req-overrides)]
    (go-catching
      (let [{:keys [error] :as resp} (<? (issue-request! req))]
        (if (not-empty error)
          (throw (Exception. (pr-str error)))
          resp)))))

(defn issue* [target content & [req-overrides]]
  (-> (issue target content req-overrides) <?! :body))

(defn await-status! [table status]
  (go-catching
    (loop []
      (let [status' (-> (issue* :describe-table {:table-name table})
                        <?
                        :table
                        :table-status)]

        (cond (nil? status')     nil
              (= status status') status'
              :else (do
                      (<? (async/timeout 1000))
                      (recur)))))))

(defn await-status!! [table status]
  (<?! (await-status! table status)))

(defn keys= [exp act ks]
  (= (select-keys exp ks) (select-keys act ks)))

(defn maps= [exp act]
  (keys= exp act (keys exp)))

(def attr       (partial zipmap [:attribute-name :attribute-type]))
(def throughput (partial zipmap [:read-capacity-units :write-capacity-units]))
(def key-schema (partial zipmap [:attribute-name :key-type]))

(def table :eulalie-test-table)

(def create-table-req
  {:table-name table
   :attribute-definitions
   [(attr [:name :S]) (attr [:age :N]) (attr [:job :S])]
   :key-schema
   [(key-schema [:name :hash]) (key-schema [:age :range])]
   :global-secondary-indexes
   [{:index-name :amazing-index
     :key-schema [(key-schema [:age :hash])]
     :projection
     {:non-key-attributes [:job]
      :projection-type :include}
     :provisioned-throughput (throughput [2 2])}]
   :local-secondary-indexes
   [{:index-name :local-index
     :key-schema [(key-schema [:name :hash])
                  (key-schema [:job  :range])]
     :projection
     {:projection-type :all}}]
   :provisioned-throughput
   (throughput [1 1])})

(def tables {table create-table-req})

(use-fixtures :once
  (fn [f]
    (when-not (issue* :describe-table {:table-name table})
      (issue* :create-table create-table-req)
      (await-status!! table :active))
    (f)))

(defn sets= [x y]
  (= (set x) (set y)))

(defn tidy-index [m]
  (reduce
   dissoc-in
   m
   [[:index-status]
    [:index-size-bytes]
    [:item-count]
    [:provisioned-throughput :number-of-decreases-today]]))

(defn matches-create-request [req resp]
  (let [[lsi-out lsi-in] (map :local-secondary-indexes  [req resp])
        [gsi-out gsi-in] (map :global-secondary-indexes [req resp])
        [req resp] (map #(update-in % [:attribute-definitions] set) [req resp])]
    (is (sets= lsi-out (map tidy-index lsi-in)))
    (is (sets= gsi-out (map tidy-index gsi-in )))
    (is (keys= req resp #{:key-schema :table-name :attribute-definitions}))))

(deftest ^:integration ^:aws describe-table
  (->> (issue* :describe-table {:table-name table})
       :table
       (matches-create-request create-table-req)))

(def item-attrs {:name {:S "Moe"} :age {:N "29"}})

(def batch-write #(issue* :batch-write-item {:request-items %}))

(defn clean-items! [table key-sets]
  (batch-write
   {table (map (fn [ks] {:delete-request {:key ks}}) key-sets)}))

(defn with-items* [items f]
  (batch-write
   (into {}
     (for [[t is] items]
       [t (map (fn [i] {:put-request {:item i}}) is)])))

  (let [keys-by-table
        (into {}
          (for [t (keys items)]
            [t (->> t tables :key-schema (map :attribute-name) (into #{}))]))]
    (try
      (f)
      (finally
        (doseq [[t is] items]
          (clean-items! t (map #(->> t keys-by-table (select-keys %)) is)))))))

(defmacro with-items [items & body]
  `(with-items* ~items (fn [] ~@body)))

(deftest ^:integration ^:aws put-item-conditional
  (let [attrs (assoc item-attrs :job {:S "Fluffer"})]
    (with-items {table [attrs]}
      (is (= {:attributes attrs}
             (issue*
              :put-item
              {:table-name table
               :item (assoc attrs :job {:S "Programmer"})
               :expression-attribute-values
               {":maximum" {:N "30"}
                ":minimum" {:N "28"}
                ":job"     {:S "Fluffer"}}
               :expression-attribute-names {:#IQ :age}
               :condition-expression (str "#IQ between :minimum and :maximum"
                                          " and job = :job")
               :return-values :all-old})))
      (is (= {:item {:job {:S "Programmer"}}}
             (issue* :get-item {:table-name table
                                :key item-attrs
                                :attributes-to-get [:job]}))))))

(deftest ^:integration ^:aws put-item-container-types
  (let [attrs (assoc item-attrs
                     :possessions  {:SS ["Shoelaces" "marker"]}
                     :measurements {:NS ["40" "34"]}
                     :garbage      {:M  {:KEy {:L [{:SS ["S"]} {:N "3"}]}}})]

    (with-items {table [attrs]}
      (is (= {:item attrs}
             (issue* :get-item {:table-name table :key item-attrs}))))))

(deftest ^:integration ^:aws update-item
  (with-items {table [item-attrs]}
    (is (= {:attributes {:IQ {:N "15"}}}
           (issue*
            :update-item
            {:table-name table
             :key item-attrs
             :expression-attribute-names {:#alias :IQ}
             :expression-attribute-values {":initial"   {:N "15"}}
             :update-expression "SET #alias = :initial"
             :return-values :updated-new})))))

(deftest ^:integration ^:aws query
  (let [attrs {:name {:S "Joe"}
               :age  {:N "15"}
               :job  {:S "Employed"}
               :Possessions {:SS #{"tin wheelbarrow"}}}]

    (with-items {table [item-attrs attrs]}
      (is (maps=
           {:items [(select-keys attrs #{:age})]}
           (issue*
            :query
            {:table-name table
             :key-conditions
             {:name
              {:attribute-value-list [{:S "Joe"}] :comparison-operator :eq}}
             :conditional-operator :and
             :query-filter
             {:job
              {:attribute-value-list [{:S "nempl"}]
               :comparison-operator :not-contains}
              :Possessions
              {:attribute-value-list [{:SS #{"tin wheelbarrow"}}]
               :comparison-operator :eq}
              :fictitious {:comparison-operator :null}}
             :attributes-to-get [:age]}))))))

(deftest ^:integration ^:aws query-expression
  (let [attrs (assoc item-attrs :job {:S "Programmer"})]
    (with-items {table [attrs]}
      (is (maps=
           {:items [(select-keys attrs #{:age :job})]}
           (issue*
            :query
            {:table-name table
             :key-conditions
             {:name {:attribute-value-list [{:S "Moe"}]
                     :comparison-operator :eq}}
             :expression-attribute-names {:#j :job}
             :expression-attribute-values
             {":s1" {:S "Programmer"}
              ":s2" {:S "Fluffer"}}
             :filter-expression "#j = :s1 OR #j = :s2"
             :projection-expression [:age :#j]}))))))

(defn item
  ([name age extra] (merge (item name age) extra))
  ([name age] {:name {:S name} :age {:N age}}))

(deftest ^:integration ^:aws scan
  (with-items {table [(item "Moe" "29" {:job {:S "Fluffer"}})
                      (item "Joe" "46" {:job {:S "Programmer"}})
                      (item "Paul" "1" {:job {:S "Ambassador"}})]}
    (let [{:keys [consumed-capacity] :as resp}
          (issue*
           :scan
           {:table-name table
            :scan-filter {:job {:attribute-value-list
                                [{:S "Fluffer"} {:S "Programmer"}]
                                :comparison-operator :between}}
            :return-consumed-capacity :total
            :select :count})]
      (is (maps= {:count 2} resp))
      (is (maps= {:table-name table} consumed-capacity)))))

(deftest ^:integration ^:aws scan-expression
  (let [target (item "Moe" "29" {:job {:S "Fluffer"}})]
    (with-items {table [target
                        (item "Joe" "30" {:job {:S "Ambassador"}})]}
      (is (maps=
           {:items [(select-keys target #{:name :age})]}
           (issue*
            :scan
            {:table-name table
             :expression-attribute-names {:#j :job :#name :name}
             :expression-attribute-values
             {":s1" {:S "Fluffer"}
              ":s2" {:S "Programmer"}}
             :filter-expression ":s1 <= #j AND #j <= :s2"
             :projection-expression [:age :#name]}))))))

(defn batch-get [m]
  (-> (issue* :batch-get-item {:request-items m})
      (update-in [:responses] #(mapvals set %))))

(deftest ^:integration ^:aws batch-get-item
  (let [target1 (item "Moe" "29")
        target2 (item "Joe" "56")]

    (with-items {table [target1 target2 (item "Irrelevant" "56")]}
      (is (maps=
           {:responses {table #{{:name {:S "Moe"}}
                                {:name {:S "Joe"}}}}}
           (batch-get
            {table {:projection-expression [:#name]
                    :consistent-read true
                    :expression-attribute-names {:#name :name}
                    :keys [target1 target2]}}))))))

(deftest ^:integration ^:aws batch-write-item
  (let [create1 (item "Joe"   "56")
        create2 (item "James" "23")
        delete1 (item "Rick"  "12")
        delete2 (item "Paul"   "7")]

    (with-items {table [delete1 delete2]}
      (issue*
       :batch-write-item
       {:request-items
        {table [{:delete-request {:key  delete1}}
                {:delete-request {:key  delete2}}
                {:put-request    {:item create1}}
                {:put-request    {:item create2}}]}})
      (is (maps= {:responses {table #{create1 create2}}}
                 (batch-get {table {:keys [create1 create2 delete1 delete2]}}))))))

(deftest list-tables
  (is (some keyword? (:table-names (issue* :list-tables {})))))

(deftest ^:integration ^:aws delete-item
  (with-items {table [(item "Moe" "29" {:job {:S "Programmer"}})]}
    (is (empty? (issue*
                 :delete-item
                 {:table-name table
                  :key        item-attrs
                  :expected   {:job
                               {:attribute-value-list [{:S "er"}]
                                :comparison-operator :contains}}})))))

(deftest ^:integration ^:aws retry-skew
  (let [{:keys [retries error] :as m}
        (<?! (issue
              :put-item
              {:table-name table :item item-attrs}
              {:max-retries 1 :time-offset (* 1000 -60 30)}))]
    (is (= {:item {:name {:S "Moe"}}}
           (issue* :get-item {:table-name table
                              :key item-attrs
                              :attributes-to-get [:name]})))))
