(ns eulalie.http
  (:require [#? (:clj clojure.core.async :cljs cljs.core.async) :as async]
            [glossop.util :refer [close-with!]]
            [kvlt.core :as kvlt]
            [promesa.core :as promesa]))

(defn req->ring [{:keys [endpoint] :as m}]
  (if-not endpoint
    m
    (-> m (dissoc :endpoint) (assoc :url (str endpoint)))))

(defn channel-request! [m]
  (let [ch (async/chan)]
    (promesa/branch (kvlt/request! m)
      (partial close-with! ch)
      (fn [e]
        (let [{:keys [status] :as m} (ex-data e)]
          (close-with! ch (assoc m :transport (= 0 status))))))
    ch))

(def request! (comp channel-request! req->ring))
(defn get! [url]
  (request! {:url (str url)}))
