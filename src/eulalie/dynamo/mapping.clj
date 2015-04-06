(ns eulalie.dynamo.mapping
  (:require
   [eulalie.util    :refer :all]
   [eulalie.dynamo.key-types :as key-types]
   [clojure.algo.generic.functor :as functor]
   [clojure.string  :as str]
   [camel-snake-kebab.core
    :refer [->SNAKE_CASE_KEYWORD ->kebab-case-keyword ->CamelCaseKeyword]]))

(defn handle-attr [f v]
  (functor/fmap
   (fn [inner]
     (if (map? inner)
       (f inner)
       (map f inner)))
   v))

(defn transform [keyf canonf typef types m]
  (into {}
    (for [[k v] m]
      [(keyf k)
       (if-let [vf (some-> k canonf typef types)]
         (vf v)
         v)])))

(defn transform-request [m]
  (transform
   ->CamelCaseKeyword
   identity
   key-types/request-key-types
   {:nest transform-request
    :attr #(handle-attr transform-request %)
    :enum ->SNAKE_CASE_KEYWORD
    :list (fn-some->> not-empty (map name) (str/join ","))}
   m))

(defn transform-response [m]
  (transform
   ->kebab-case-keyword
   ->kebab-case-keyword
   key-types/response-key-types
   {:nest transform-response
    :attr #(handle-attr transform-response %)
    :enum ->kebab-case-keyword
    :keys #(functor/fmap keyword %)
    :list #(and (not-empty %)
                (map keyword (str/split % #",")))}
   m))
