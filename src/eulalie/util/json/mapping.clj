(ns eulalie.util.json.mapping
  (:require
   [eulalie]
   [eulalie.util :refer :all]
   [clojure.algo.generic.functor :as functor]
   [clojure.string  :as str]
   [camel-snake-kebab.core
    :refer [->SCREAMING_SNAKE_CASE_KEYWORD ->kebab-case-keyword ->PascalCaseKeyword]]))

;; This stuff is kind of weird, but I'm afraid to touch it

(defn handle-attr [f v]
  (functor/fmap
   (fn [inner]
     (if (map? inner)
       (f inner)
       (map f inner)))
   v))

(defn transform [cont keyf canonf typef types m]
  (into {}
    (for [[k v] m]
      [(keyf k)
       (let [type (some-> k canonf typef)]
         (cond
           (not type)     v
           (= type :nest) (if (map? v)
                            (cont v)
                            (map cont v))
           (types type) ((types type) v)
           :else v))])))

(defn transform-request [m key-types]
  (let [continue #(transform-request % key-types)]
    (transform
     continue
     ->PascalCaseKeyword
     identity
     key-types
     {:attr (partial handle-attr continue)
      :enum ->SCREAMING_SNAKE_CASE_KEYWORD
      :list (fn-some->> not-empty (map name) (str/join ","))}
     m)))

(defn transform-response [m key-types]
  (let [continue #(transform-response % key-types)]
    (transform
     continue
     ->kebab-case-keyword
     ->kebab-case-keyword
     key-types
     {:attr (partial handle-attr continue)
      :enum ->kebab-case-keyword
      :keys #(functor/fmap keyword %)
      :list #(and (not-empty %)
                  (map keyword (str/split % #",")))}
     m)))
