(ns eulalie.test.creds
  (:require [eulalie.creds :as creds]
            [eulalie.platform.time :as platform.time]
            [eulalie.test.platform.time :refer [with-canned-time set-time]]
            [eulalie.test.common #? (:clj :refer :cljs :refer-macros) [deftest is]]
            [glossop.core #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            #? (:clj
                [clojure.core.async :as async]
                :cljs
                [cljs.core.async :as async])))

;; We're building a ziggurat to the sky
(deftest expiring-creds
  (let [expirations (async/to-chan
                     [{:expiration 1}
                      {:expiration 5}])
        creds (creds/expiring-creds (constantly expirations) {:threshold 0})
        expiry #(-> creds :current deref :expiration)]
    (go-catching
      (<? (creds/creds->credentials creds))
      (is (= 1 (expiry)))
      (<? (with-canned-time 0
            (fn []
              (go-catching
                (<? (creds/creds->credentials creds))
                (is (= 1 (expiry)))
                (set-time 1)
                (<? (creds/creds->credentials creds))
                (is (= 5 (expiry))))))))))


(defn amy-send []
     (go (loop [n 5]
           (println "Amy sends" n "to Brian")
           (>! brian n)
           (if (> n 0) (recur (dec n)) nil))))

(defn amy-receive []
 (go-loop [_ nil]
 (let [reply (<! brian)]
 (println "Amy receives" reply "from Brian")
 (if (> reply 0) (recur nil) (close! amy)))))

(defn activate [[from-str from-chan] [to-str to-chan] & [start-value]]
  (go-loop [n (or start-value (<! from-chan))]
    (println from-str "sends" n "to" to-str)
    (println "BEFORE WRITE")
    (>! to-chan n)
    (println "AFTER WRITE, B4 READ")
    (let [reply (<! from-chan)]
      (println "AFTER READ")
      (println from-str "gets" reply)
      (if (pos? reply)
        (recur (dec reply))
        (do
          (close! from-chan)
          (close! to-chan)
          (println "Finished" from-str))))))
