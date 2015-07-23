(ns eulalie.test.dynamo
  (:require #?@(:clj
                [[clojure.test :refer [is]]
                 [glossop.core :refer [go-catching <?]]
                 [eulalie.test.async :refer [deftest]]]
                :cljs
                [[glossop.core]
                 [cemerick.cljs.test]])
            [clojure.walk :as walk]
            [eulalie.core :as eulalie]
            [eulalie.dynamo]
            [eulalie.test.dynamo.common :as dynamo.common :refer
             [with-local-dynamo! with-remote-dynamo! issue! batch-get! table]]
            [plumbing.core :refer [dissoc-in]])
  #? (:cljs (:require-macros [cemerick.cljs.test :refer [is]]
                             [eulalie.test.async.macros :refer [deftest]]
                             [glossop.macros :refer [go-catching <?]])))

(defn keys= [exp act ks]
  (= (select-keys exp ks) (select-keys act ks)))

(defn sets= [x y]
  (= (set x) (set y)))

(def unordered
  (partial
   walk/postwalk
   (fn [form]
     (if (map? form)
       (into {}
         (for [[k v] form]
           [k (cond->> v (#{:SS :NS :BS} k) (into #{}))]))
       form))))

(defn tidy-index [m]
  (reduce
   dissoc-in
   m
   [[:index-status]
    [:index-size-bytes]
    [:item-count]
    [:index-arn]
    [:provisioned-throughput :number-of-decreases-today]]))

(defn matches-create-request [req resp]
  (let [[lsi-out lsi-in] (map :local-secondary-indexes  [req resp])
        [gsi-out gsi-in] (map :global-secondary-indexes [req resp])
        [req resp] (map #(update-in % [:attribute-definitions] set) [req resp])]
    (and (sets= lsi-out (map tidy-index lsi-in))
         (sets= gsi-out (map tidy-index gsi-in ))
         (keys= req resp #{:key-schema :table-name :attribute-definitions}))))

(deftest ^:integration describe-table
  (with-local-dynamo! []
    #(go-catching
       (is (->> (issue! % :describe-table {:table-name table})
                <?
                :table
                (matches-create-request dynamo.common/create-table-req))))))

(def item-attrs  {:name {:S "Moe"} :age {:N "30"}})

(deftest ^:integration update-item
  (with-local-dynamo! [item-attrs]
    #(go-catching
       (let [item (<? (issue!
                       %
                       :update-item
                       {:table-name table
                        :key item-attrs
                        :expression-attribute-names {:#alias :gold}
                        :expression-attribute-values {":initial" {:N "15"}}
                        :update-expression "SET #alias = :initial"
                        :return-values :updated-new}))]
         (is (= item {:attributes {:gold {:N "15"}}}))))))

(deftest ^:integration put-item-container-types
  (let [attrs (assoc item-attrs
                     :possessions  {:SS ["Shoelaces" "\u00a5123Hello"]}
                     :measurements {:NS ["40" "34"]}
                     :garbage      {:M  {:KEy {:L [{:SS ["S"]} {:N "3"}]}}})]
    (with-local-dynamo! [attrs]
      #(go-catching
         (let [item (<? (issue! % :get-item {:table-name table :key item-attrs}))]
           (is (= (unordered item) (unordered {:item attrs}))))))))

(deftest ^:integration batch-get-item
  (let [target1 {:name {:S "Moe"} :age {:N "30"}}
        target2 {:name {:S "Joe"} :age {:N "56"}}]
    (with-local-dynamo! [target1 target2
                         {:name {:S "Other Guy"} :age {:N "15"}}]
      (fn [creds]
        (go-catching
          (let [{:keys [responses]}
                (<? (batch-get!
                     creds
                     {table {:projection-expression [:#name]
                             :consistent-read true
                             :expression-attribute-names {:#name :name}
                             :keys [target1 target2]}}))]
            (is (= responses
                   {table #{{:name {:S "Moe"}} {:name {:S "Joe"}}}}))))))))

(deftest ^:integration list-tables
  (with-local-dynamo!
    []
    (fn [creds]
      (go-catching
        (is (some keyword? (:table-names (<? (issue! creds :list-tables {})))))))))

(deftest ^:integration ^:aws retry-skew
  (with-remote-dynamo! []
    (fn [creds]
      (go-catching
        (let [item (assoc item-attrs :test-name {:S "retry-skew"})
              {:keys [retries error]}
              (<? (test.common/issue-raw!
                   {:service :dynamo
                    :target :put-item
                    :creds creds
                    :body {:table-name table :item item}
                    :max-retries 1
                    :time-offset (* 1000 -60 30)}))]
          (is (= 1 retries))
          (is (empty? error))
          (is (= item
                 (:item (<? (issue! creds :get-item {:table-name table
                                                     :key item-attrs}))))))))))
