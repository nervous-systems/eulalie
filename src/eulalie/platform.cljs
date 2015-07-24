(ns eulalie.platform
  (:require [cemerick.url :as url]
            [cljs.core.async :as async :refer [>!]]
            [cljs.nodejs :as nodejs]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [eulalie.platform.util :refer [channel-fn]]
            [eulalie.util.service :refer [concretize-port]]
            [glossop.core :as glossop :refer-macros [go-catching <?]]
            [glossop.util :as glossop.util :refer [close-with!]]))

(def http  (nodejs/require "http"))
(def https (nodejs/require "https"))
(def buffer-crc32 (nodejs/require "buffer-crc32"))

(defn req->node [{{:keys [query host port path]} :endpoint :keys [headers method] :as req}]
  (cond->
      {:host   host
       :path   (cond-> path query (str "?" (url/map->query query)))
       :method method
       :headers headers}
    port (assoc :port port)))

(defn request! [{body :body {:keys [protocol] :as u} :endpoint :as req} & [{:keys [chan]}]]
  (let [ch       (or chan (async/chan 10))
        node-req (.request
                  (case protocol "http" http "https" https)
                  (-> req (assoc :endpoint (concretize-port u)) req->node clj->js))]
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

(defn channel-request! [req]
  (go-catching
    (try
      (let [ch    (request! req)
            resp  (<? ch)
            body  (<? (glossop.util/reduce str "" ch))]
        (assoc resp :body body))
      (catch js/Error e
        {:error e}))))

(defn http-get! [url]
  (channel-request! {:method :get
                     :endpoint (cond-> url (string? url) url/url)}))

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
        (= input-crc (.unsigned buffer-crc32 (get-utf8-bytes body))))))

(defn encode-json [x]
  (.stringify js/JSON (clj->js x)))

(defn encode-base64 [x]
  (.toString (js/Buffer. x "utf8") "base64"))

(defn decode-base64 [x]
  (.toString (js/Buffer. x "base64") "utf8"))

(defn byte-array? [x]
  (instance? js/Buffer x))

(defn ba->b64-string [x]
  (.toString x "base64"))

(defn b64-string->ba [x]
  (js/Buffer. x "base64"))
