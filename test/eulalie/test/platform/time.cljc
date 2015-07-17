(ns eulalie.test.platform.time
  (:require [cljs.nodejs :as nodejs]
            [eulalie.platform.time :as platform.time])
  (:require-macros [glossop.macros :refer [<? go-catching]]))

(def timekeeper (nodejs/require "timekeeper"))

(defn with-canned-time [t f & args]
  (.freeze timekeeper (platform.time/to-long t))
  (go-catching
    (try
      (<? (apply f args))
      (finally
        (.reset timekeeper)))))
