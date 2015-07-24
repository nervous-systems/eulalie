(ns eulalie.test.platform.time
  (:require [cljs.nodejs :as nodejs]
            [eulalie.platform.time :as platform.time]
            [glossop.core :refer-macros [<? go-catching]]))

(def timekeeper (nodejs/require "timekeeper"))

(defn set-time [x]
  (.freeze timekeeper (platform.time/to-long x)))

(defn with-canned-time [t f & args]
  (set-time t)
  (go-catching
    (try
      (<? (apply f args))
      (finally
        (.reset timekeeper)))))
