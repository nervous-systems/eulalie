(ns eulalie.platform
  (:require [cemerick.url :as url]
            [cljs.core.async :as async :refer [>!]]
            [cljs.nodejs :as nodejs]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [eulalie.platform.util :refer [channel-fn]]
            [glossop.core :as glossop]
            [glossop.util :refer [close-with!]])
  (:require-macros [glossop.macros :refer [go-catching <?]]))

(def http  (nodejs/require "http"))
(def https (nodejs/require "https"))
(def crc   (nodejs/require "crc"))

(defn req->node [{{:keys [query host port path]} :endpoint :keys [headers method] :as req}]
  (cond->
      {:host   host
       :path   (cond-> path query (str "?" (url/map->query query)))
       :method method
       :headers headers}
    port (assoc :port port)))

(defn request! [{body :body {:keys [protocol]} :endpoint :as req} & [{:keys [chan]}]]
  (let [ch       (or chan (async/chan 10))
        node-req (.request
                  (case protocol "http" http "https" https)
                  (clj->js (req->node req)))]
    (.on node-req "response"
         (fn [resp]
           (if (glossop/error? resp)
             (close-with! ch resp)
             (let [headers (aget resp "headers")
                   status  (aget resp "statusCode")]
               (async/put! ch {:headers (js->clj headers :keywordize-keys true)
                               :status status})
               (.on resp "data"  (partial async/put! ch))
               (.on resp "error" (partial close-with! ch))
               (.on resp "end"   #(async/close! ch))))))
    (.on node-req "error" (partial close-with! ch))
    (.end node-req body)
    ch))

(defn reduce* [f init ch]
  (go-catching
    (loop [ret init]
      (let [v (<? ch)]
        (if (nil? v)
          ret
          (recur (f ret v)))))))

(defn channel-request! [req]
  (go-catching
    (try
      (let [ch    (request! req)
            resp  (<? ch)
            body  (<? (reduce* str "" ch))]
        (assoc resp :body body))
      (catch js/Error e
        {:error e}))))

(defn http-get! [url]
  (channel-request! {:method :get :endpoint url}))

(defn channel-aws-request! [req]
  (channel-request! req))

(defn decode-json [s]
  (js->clj (.parse js/JSON s) :keywordize-keys true))

(defn http-response->error [error]
  (when error
    (let [code (-> error (aget "code") keyword)]
      {:type ({:ENOTFOUND :unknown-host} code code)
       :message (aget error "message")
       :transport true})))

(def byte-count #(.byteLength js/Buffer % "utf8"))

(defn get-utf8-bytes [s]
  (js/Buffer. s "utf8"))

(defn response-checksum-ok? [{:keys [headers body]}]
  (let [input-crc (some-> headers :x-amz-crc32 js/Number)]
    (or (not input-crc)
        (= (:content-encoding headers) "gzip")
        (= (.crc32 crc (get-utf8-bytes body))))))

(defn encode-json [x]
  (.stringify js/JSON (clj->js x)))

(defn encode-base64 [x]
  (.toString (js/Buffer. x "utf8") "base64"))

(defn decode-base64 [x]
  (.toString (js/Buffer. x "base64") "utf8"))
