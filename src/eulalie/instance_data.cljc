(ns eulalie.instance-data
  (:require #?@(:clj
                [[glossop.core :refer [go-catching <? <?!]]
                 [clj-time.format :as time.format]
                 [clj-time.coerce :as time.coerce]]
                :cljs
                [[cljs.core.async]
                 [cljs-time.format :as time.format]
                 [cljs-time.coerce :as time.coerce]])
            [clojure.string :as str]
            [eulalie.platform :as platform]
            [eulalie.util :as util]
            [clojure.set :as set]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras])
  #?(:cljs (:require-macros [glossop.macros :refer [go-catching <?]])))

(defn- parse-json-body [x]
  ;; Amazon's war against Content-Type continues
  (if (and x (pos? (count x)) (= (subs x 0 1) "{"))
    (csk-extras/transform-keys csk/->kebab-case-keyword (platform/decode-json x))
    x))

(defn retrieve!
  "(retrieve! [:latest :dynamic :instance-identity :document] {:parse-json true})"
  [path &
   [{:keys [host parse-json]
     :or {host "instance-data.ec2.internal"
          parse-json false}}]]
  (let [path (cond-> path (not (coll? path)) vector)
        url  (str "http://" host ":80/"
                  (str/join "/"
                            (map name path)))]
    (go-catching
      (let [{:keys [error body status] :as response}
            (<? (platform/http-get! url))]
        (cond error error
              (= status 200) (cond-> body parse-json parse-json-body)
              :else nil)))))
#?(:clj (def retrieve!! (comp <?! retrieve!)))

(defn metadata!
  "(metadata! [:iam :security-credentials])"
  [path & [args]]
  (retrieve! (flatten (conj [:latest :meta-data] path)) args))
#?(:clj (def meta-data!! (comp <?! metadata!)))

(defn instance-identity!
  "(instance-identity! :document {:parse-json true})"
  [path & [args]]
  (retrieve!
   (flatten (conj [:latest :dynamic :instance-identity] path))
   args))

#?(:clj (def instance-identity!! (comp <?! instance-identity!)))

(defn identity-key! [k]
  (go-catching
    (-> (instance-identity! :document {:parse-json true})
        <?
        (get (keyword k)))))
#?(:clj (def identity-key!! (comp <?! identity-key!)))

(defn default-iam-role! []
  (go-catching
    (some-> (metadata! [:iam :security-credentials]) <?
            (util/to-first-match "\n") not-empty)))
#?(:clj (def default-iam-role!! (comp <?! default-iam-role!)))

(let [seconds-formatter (time.format/formatters :date-time-no-ms)]
  (defn from-iso-seconds [x]
    (time.coerce/to-long (time.format/parse seconds-formatter x))))

(defn- tidy-iam-creds [m]
  (-> m
      (set/rename-keys {:access-key-id :access-key
                        :secret-access-key :secret-key})
      (update-in [:expiration] from-iso-seconds)))

(defn iam-credentials! [role]
  (go-catching
    (-> (metadata!
         [:iam :security-credentials (name role)]
         {:parse-json true})
        <?
        tidy-iam-creds)))
#?(:clj (def iam-credentials!! (comp <?! iam-credentials!)))

(defn default-iam-credentials! []
  (go-catching
    (when-let [default-role (<? (default-iam-role!))]
      (<? (iam-credentials! default-role)))))
#?(:clj (def default-iam-credentials!! (comp <?! default-iam-credentials!)))
