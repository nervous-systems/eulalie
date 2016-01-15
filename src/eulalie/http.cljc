(ns eulalie.http
  (:require [kvlt.core :as kvlt]
            [promesa.core :as promesa]
            [#? (:clj
                 clojure.core.async
                 :cljs
                 cljs.core.async) :as async]))

(defn req->ring [{:keys [endpoint headers] :as m}]
  (-> m
      (dissoc :endpoint)
      (assoc :url (str endpoint))))

(defn channel-request! [m]
  (let [ch (async/chan)]
    (promesa/branch (kvlt/request! (assoc m :kvlt/trace true))
      #(async/put! ch %)
      (fn [e]
        (let [{:keys [status] :as m} (ex-data e)]
          (async/put! ch (assoc m :transport (= 0 status))))))
    ch))

(defn http-get! [url]
  (channel-request! {:url (str url)}))

(defn channel-aws-request! [m]
  (channel-request! (req->ring m)))
