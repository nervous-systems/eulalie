(ns eulalie.test.platform.time
  (:require [glossop.core :refer [<? go-catching]]
            [eulalie.platform.time :as platform.time])
  (:import org.joda.time.DateTimeUtils))

(defn set-time [x]
  (DateTimeUtils/setCurrentMillisFixed (platform.time/to-long x)))

(defn with-canned-time [t f & args]
  (set-time t)
  (go-catching
    (try
      (<? (apply f args))
      (finally
        (DateTimeUtils/setCurrentMillisOffset 0)))))
