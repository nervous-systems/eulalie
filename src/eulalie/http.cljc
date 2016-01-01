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
    (promesa/branch (kvlt/request! m)
      #(async/put! ch %)
      #(async/put! ch (assoc (ex-data %) :transport true)))
    ch))

(defn http-get! [url]
  (channel-request! {:url (str url)}))

(defn channel-aws-request! [m]
  (channel-request! (req->ring m)))
