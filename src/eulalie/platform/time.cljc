(ns eulalie.platform.time
  #? (:clj
      (:require
       [clj-time.core   :as time]
       [clj-time.coerce :as time-coerce]
       [clj-time.format :as time-format])
      :cljs
      (:require
       [cljs-time.core   :as time]
       [cljs-time.coerce :as time-coerce]
       [cljs-time.format :as time-format])))

(defn msecs-now []
  (time-coerce/to-long (time/now)))

(def to-long   time-coerce/to-long)
(def from-long time-coerce/from-long)

;; clj-time's :rfc882 formatter uses Z, whereas RFC 882 specifies the
;; equivalent of either Z or z.  AWS uses z.
#? (:clj
    (let [fmt (time-format/formatter "EEE, dd MMM yyyy HH:mm:ss z")]
      (def rfc822->time (partial time-format/parse fmt))
      (def time->rfc822 (partial time-format/unparse fmt)))
    ;; cljs-time doesn't support named timezones, and we only need a couple
    :cljs
    (do
      (defn rfc822->time [s]
        (time-coerce/from-long (.parse js/Date s)))
      (defn time->rfc822 [t]
        (.toUTCString (js/Date. (time-coerce/to-long t))))))

(def aws-date-format (time-format/formatters :basic-date))
(def aws-date->time  (partial time-format/parse aws-date-format))

(def aws-date-time-format (time-format/formatters :basic-date-time-no-ms))

(def ->aws-date-time (partial time-format/unparse aws-date-time-format))
(def ->aws-date (partial time-format/unparse aws-date-format))
