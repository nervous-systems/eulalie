(ns eulalie.service.impl.json.mapping
  (:require [clojure.string :as str]
            [camel-snake-kebab.core :as csk]))

;; this is weird, old, working

(defn- fmap [f x]
  (cond
    (map?  x) (into (empty x) (for [[k v] x] [k (f v)]))
    (set?  x) (into (empty x) (map f x))
    (coll? x) (map f x)
    (nil?  x) x
    :else     (f x)))

(defn- handle-attr [f v]
  (fmap
   (fn [inner]
     (if (map? inner)
       (f inner)
       (map f inner)))
   v))

(defn- transform [cont keyf key->type types m]
  (into {}
    (for [[k v] m :when (not (namespace k))]
      [(keyf k)
       (let [type (key->type k)]
         (cond
           (not type)     v
           (= type :nest) (if (map? v)
                            (cont v)
                            (map cont v))
           (types type)   ((types type) v)
           (fn?   type)   (type v)
           :else          v))])))

(defn transform-request [m key-types & [{:keys [renames] :or {renames {}}}]]
  (let [continue #(transform-request % key-types)]
    (transform
     continue
     #(or (renames (keyword %)) (csk/->PascalCaseKeyword %))
     key-types
     {:attr (partial handle-attr continue)
      :list #(str/join "," (map name %))}
     m)))

(defn transform-response [m key-types & [{:keys [renames] :or {renames {}}}]]
  (let [continue #(transform-response % key-types)]
    (transform
     continue
     #(or (renames (keyword %)) (csk/->kebab-case-keyword %))
     (comp key-types csk/->kebab-case-keyword)
     {:attr (partial handle-attr continue)
      :keys (partial fmap keyword)
      :list #(if (not-empty %)
               (map keyword (str/split % #","))
               '())}
     m)))
