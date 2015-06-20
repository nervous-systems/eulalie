(ns eulalie.instance-data
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.string :as str]
            [eulalie.util :as util :refer [go-catching <?]]
            [cheshire.core :as json]
            [clojure.set :as set]
            [camel-snake-kebab.core :as csk]
            [clj-time.format :as time.format]
            [clj-time.coerce :as time.coerce]))

(defn parse-json-body [x]
  ;; Amazon's war against Content-Type continues
  (cond-> x (and x (pos? (count x)) (= (subs x 0 1) "{"))
          (json/decode csk/->kebab-case-keyword)))

(defn retrieve!
  "(retrieve! :local-ipv4)
   (retrieve! [:iam :security-credentials :xyz])"
  [path &
   [{:keys [url parse-json]
     :or {url "http://169.254.169.254/latest/meta-data"
          parse-json false}}]]
  (let [path (cond-> path (not (coll? path)) vector)
        url  (str/join "/" (into [url] (map name path)))]
    (go
      (let [{:keys [error body status] :as response}
            (<! (util/channel-request! {:url url}))]
        (cond error error
              (= status 200) (cond-> body parse-json parse-json-body)
              :else nil)))))

(def retrieve!! (comp util/<?! retrieve!))

(defn default-iam-role! []
  (go-catching
    (some-> (retrieve! [:iam :security-credentials]) <?
            (util/to-first-match "\n") not-empty)))
(def default-iam-role!! (comp util/<?! default-iam-role!))

(let [seconds-formatter (time.format/formatters :date-time-no-ms)]
  (defn from-iso-seconds [x]
    (time.coerce/to-long (time.format/parse seconds-formatter x))))

(defn tidy-iam-creds [m]
  (-> m
      (set/rename-keys {:access-key-id :access-key
                        :secret-access-key :secret-key})
      (update-in [:expiration] from-iso-seconds)))

(defn iam-credentials! [role]
  (go-catching
    (-> (retrieve!
         [:iam :security-credentials (name role)]
         {:parse-json true})
        <?
        tidy-iam-creds)))
(def iam-credentials!! (comp util/<?! iam-credentials!))

(defn default-iam-credentials! []
  (go-catching
    (when-let [default-role (<? (default-iam-role!))]
      (<? (iam-credentials! default-role)))))
(def default-iam-credentials!! (comp util/<?! default-iam-credentials!))

(defn retrieve-many!
  "(retrieve-many! [:local-ipv4 :other-key])
  -> {:local-ipv4 ... other-key ...}"
  [ks]
  (go-catching
    (let [ch->tag (into {} (for [k ks] [(retrieve! k) k]))
          tag-ch  (util/merge-tagged ch->tag (async/buffer (count ks)))]
      (util/mapvals util/throw-err (<! (async/into {} tag-ch))))))
(def retrieve-many!! (comp util/<?! retrieve-many!))
