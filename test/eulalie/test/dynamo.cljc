(ns eulalie.test.dynamo
  "These tests require (but don't create) a couple of Dynamo tables"
  (:require
   [cemerick.url :refer [url]]
   [eulalie.core :as eulalie]
   [eulalie.dynamo]
   [eulalie.util :as util]
   [glossop.util]
   [plumbing.core :refer [map-vals dissoc-in]]
   [eulalie.test.common :as test.common :refer [creds]]
   #?@ (:clj
        [[clojure.test :refer [is]]
         [glossop.core :refer [go-catching <?]]
         [clojure.core.async :as async]
         [eulalie.test.async :refer [deftest]]]
        :cljs
        [[glossop.core]
         [cljs.core.async :as async]
         [cemerick.cljs.test]]))
  #? (:cljs (:require-macros [cemerick.cljs.test :refer [is]]
                             [eulalie.test.async.macros :refer [deftest]]
                             [glossop.macros :refer [go-catching <?]])))

(defn keys= [exp act ks]
  (= (select-keys exp ks) (select-keys act ks)))

(defn maps= [exp act]
  (keys= exp act (keys exp)))

(defn sets= [x y]
  (= (set x) (set y)))

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

(defn issue! [target content & [req-overrides]]
  (go-catching
    (let [req (merge
               {:service :dynamo
                :target  target
                :max-retries 0
                :body content
                :creds creds}
               req-overrides)]
      (:body (<? (test.common/issue-raw! req))))))

(deftest ^:integration ^:aws describe-table
  (go-catching
    (is (->> (issue! :describe-table {:table-name table})
             <?
             :table
             (matches-create-request create-table-req)))))

(def item-attrs  {:name {:S "Moe"} :age {:N "30"}})
(def batch-write! #(issue! :batch-write-item {:request-items %}))

(defn clean-items! [table->key-sets]
  (batch-write!
   (into {}
     (for [[t key-sets] table->key-sets]
       [table (for [ks key-sets] {:delete-request {:key ks}})]))))

(defn with-items! [create->items f]
  (go-catching
    (<? (batch-write!
         (into {}
           (for [[{:keys [table-name]} items] create->items]
             [table-name (for [item items] {:put-request {:item item}})]))))

    (let [keys-by-table
          (into {}
            (for [{:keys [table-name key-schema]} (keys create->items)]
              [table-name (->> key-schema (map :attribute-name) (into #{}))]))]
      (println "ROFL")
      (try
        (<? (f))
        (finally
          (clean-items!
           (into {}
             (for [[{:keys [table-name]} items] create->items]
               [table-name
                (for [item items]
                  (select-keys item (keys-by-table table)))]))))))))

(deftest ^:integration ^:aws update-item
  (go-catching
    (let [item
          (<? (with-items! {create-table-req [item-attrs]}
                #(issue!
                  :update-item
                  {:table-name table
                   :key item-attrs
                   :expression-attribute-names {:#alias :gold}
                   :expression-attribute-values {":initial" {:N "15"}}
                   :update-expression "SET #alias = :initial"
                   :return-values :updated-new})))]
      (is (= item {:attributes {:gold {:N "15"}}})))))

(deftest ^:integration ^:aws put-item-container-types
  (let [attrs (assoc item-attrs
                     :possessions  {:SS ["Shoelaces" "\u00a5123Hello"]}
                     :measurements {:NS ["40" "34"]}
                     :garbage      {:M  {:KEy {:L [{:SS ["S"]} {:N "3"}]}}})]

    (go-catching
      (let [item (<? (with-items!
                       {create-table-req [attrs]}
                       #(issue! :get-item {:table-name table :key item-attrs})))]
        (is (= item {:item attrs}))))))

(defn batch-get! [m]
  (go-catching
    (-> (issue! :batch-get-item {:request-items m})
        <?
        (update-in [:responses] #(map-vals set %)))))

(deftest ^:integration ^:aws batch-get-item
  (go-catching
    (let [target1 {:name {:S "Moe"} :age {:N "30"}}
          target2 {:name {:S "Joe"} :age {:N "56"}}
          result  (<? (with-items! {create-table-req
                                    [target1 target2
                                     {:name {:S "Other Guy"}
                                      :age  {:N "15"}}]}
                        #(batch-get!
                          {table {:projection-expression [:#name]
                                  :consistent-read true
                                  :expression-attribute-names {:#name :name}
                                  :keys [target1 target2]}})))]
      (is (maps=
           {:responses {table #{{:name {:S "Moe"}}
                                {:name {:S "Joe"}}}}} result)))))

(deftest ^:integration ^:aws list-tables
  (go-catching
    (is (some keyword? (:table-names (<? (issue! :list-tables {})))))))

(deftest ^:integration ^:aws retry-skew
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
             (:item (<? (issue! :get-item {:table-name table
                                           :key item-attrs}))))))))
