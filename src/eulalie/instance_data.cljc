(ns eulalie.instance-data
  (:require #?@(:clj
                [[clj-time.format :as time.format]
                 [clj-time.coerce :as time.coerce]]
                :cljs
                [[cljs-time.format :as time.format]
                 [cljs-time.coerce :as time.coerce]])
            [glossop.core :as g
             #? (:clj :refer :cljs :refer-macros) [go-catching <?]]
            [#? (:clj clojure.core.async :cljs cljs.core.async) :as async]
            [clojure.string :as str]
            [eulalie.platform :as platform]
            [eulalie.util :as util]
            [clojure.set :as set]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as csk-extras]))

(defn- parse-json-body [x]
  ;; Amazon's war against Content-Type continues
  (if (and x (pos? (count x)) (= (subs x 0 1) "{"))
    (csk-extras/transform-keys csk/->kebab-case-keyword (platform/decode-json x))
    x))

(defn retrieve!
  "(retrieve! [:latest :dynamic :instance-identity :document] {:parse-json true})"
  [path &
   [{:keys [host parse-json chan close?]
     :or {host "instance-data.ec2.internal"
          close? true}}]]
  (let [path (cond-> path (not (coll? path)) vector)
        url  (str "http://" host ":80/"
                  (str/join "/"
                            (map name path)))]
    (cond->
        (go-catching
          (let [{:keys [error body status] :as response}
                (<? (platform/http-get! url))]
            (cond error error
                  (= status 200) (cond-> body parse-json parse-json-body)
                  :else nil)))
      chan (async/pipe chan close?))))

#?(:clj (def retrieve!! (comp g/<?! retrieve!)))

(defn metadata!
  "(metadata! [:iam :security-credentials])"
  [path & [args]]
  (retrieve! (flatten (conj [:latest :meta-data] path)) args))
#?(:clj (def metadata!! (comp g/<?! metadata!)))

(defn instance-identity!
  "(instance-identity! :document {:parse-json true})"
  [path & [args]]
  (retrieve!
   (flatten (conj [:latest :dynamic :instance-identity] path))
   args))

#?(:clj (def instance-identity!! (comp g/<?! instance-identity!)))

(defn identity-key! [k & [args]]
  (go-catching
    (-> (instance-identity! :document (assoc args :parse-json true))
        <?
        (get (keyword k)))))
#?(:clj (def identity-key!! (comp g/<?! identity-key!)))

(defn default-iam-role! [& [args]]
  (go-catching
    (some-> (metadata! [:iam :security-credentials] args) <?
            (util/to-first-match "\n") not-empty)))
#?(:clj (def default-iam-role!! (comp g/<?! default-iam-role!)))

(let [seconds-formatter (time.format/formatters :date-time-no-ms)]
  (defn from-iso-seconds [x]
    (time.coerce/to-long (time.format/parse seconds-formatter x))))

(defn- tidy-iam-creds [{:keys [expiration] :as m}]
  (let [m (set/rename-keys m {:access-key-id :access-key
                              :secret-access-key :secret-key})]
    (cond-> m
      expiration (assoc :expiration (from-iso-seconds expiration)))))

(defn iam-credentials! [role & [{:keys [chan close?] :or {close? true}}]]
  (cond->
      (go-catching
        (-> (metadata!
             [:iam :security-credentials (name role)]
             {:parse-json true})
            <?
            tidy-iam-creds))
    chan (async/pipe chan close?)))
#?(:clj (def iam-credentials!! (comp g/<?! iam-credentials!)))

(defn default-iam-credentials! [& [{:keys [chan close?] :or {close? true}}]]
  (cond->
      (go-catching
        (when-let [default-role (<? (default-iam-role!))]
          (<? (iam-credentials! default-role))))
    chan (async/pipe chan close?)))
#?(:clj (def default-iam-credentials!! (comp g/<?! default-iam-credentials!)))
