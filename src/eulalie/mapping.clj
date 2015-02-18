(ns eulalie.mapping
  (:require [eulalie.util :refer :all]
            [clojure.algo.generic.functor :as functor]
            [clojure.set :refer [rename-keys]]
            [camel-snake-kebab.core :refer [->CamelCaseString ->SNAKE_CASE_STRING ->kebab-case-keyword]]
            [cheshire.core :as cheshire]))

;; (defmulti transform-value (fn [dir&type value types] dir&type))

;; (defmacro deftransforms [t in-f out-f]
;;   `(do
;;      (defmethod transform-value [:in  ~t] [_# v# _#] (~in-f  v#))
;;      (defmethod transform-value [:out ~t] [_# v# _#] (~out-f v#))))

;; (deftransforms :enum
;;   (fn-some-> ->kebab-case-keyword)
;;   (fn-some-> ->SNAKE_CASE_STRING))
;; (deftransforms :opaque identity identity)

;; (defmethod transform-value [:in nil] [_ v _]
;;   v)

;; (defmethod transform-value [:out nil] [_ v _]
;;   (if (keyword? v)
;;     (fq-name v)
;;     v))

;; (deftransforms :keyword (partial functor/fmap keyword) identity)

;; (defmulti  transform-key (fn [direction k] direction))
;; (defmethod transform-key :in  [_ k] (->kebab-case-keyword k))
;; (defmethod transform-key :out [_ k] (->CamelCaseString    k))

;; (declare transform)

;; (defn transform-kv [in-out [k v] types]
;;   (let [k'    (transform-key in-out k)
;;         type  (types (if (= in-out :in) k' k))]
;;     [k' (if type
;;           (transform-value [in-out type] v types)
;;           (transform in-out v types))]))

;; (defn transform [in-out m types]
;;   (cond
;;     (map?  m) (into {} (map #(transform-kv in-out % types) m))
;;     (coll? m) (map #(transform in-out % types) m)
;;     :else     (transform-value [in-out nil] m types)))

;; (defn transform-outer [in-out m types]
;;   (transform
;;    in-out
;;    (if (= :in in-out)
;;      (clojure.walk/keywordize-keys m)
;;      m)
;;    types))

;; (def transform-request
;;   (partial transform-outer :out))

;; (def transform-response
;;   (partial transform-outer :in))

;; (defmulti prepare-map (fn [direction service m] service))

;; (defn prepared? [direction v]
;;   ((if (= direction :in)
;;      keyword?
;;      string?) v))

;; (defn transform' [direction service m]
;;   (let [m (prepare-map direction service m)]
;;     (clojure.walk/postwalk
;;      (fn [v]
;;        (if (map? v)
;;          (mapkeys #(if-not (prepared? direction %)
;;                      (transform-key direction %)
;;                      %) v)
;;          v))
;;      m)))

;; (def transform-request'  (partial transform' :out))
;; (def transform-response' (partial transform' :in))

;; (defprotocol MapTransformer
;;   (transform-request  [this m])
;;   (transform-response [this m]))
