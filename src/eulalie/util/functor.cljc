(ns eulalie.util.functor
  "The minimal amount of code required to replace clojure.algo.generic.functor")

(defn fmap [f x]
  (cond
    (map?  x) (into (empty x) (for [[k v] x] [k (f v)]))
    (set?  x) (into (empty x) (map f x))
    (coll? x) (mapv f x)
    (nil?  x) x
    :else     (f x)))
