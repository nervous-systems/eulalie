(ns eulalie.util.functor
  "The minimal amount of code required to replace clojure.algo.generic.functor")

(defn fmap [f x]
  (cond
    (map?  x) (into {}  (for [[k v] x] [k (f v)]))
    (coll? x) (into (empty x) (map f x))
    (nil?  x) x
    :else     (f x)))
