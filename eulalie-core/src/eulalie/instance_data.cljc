(ns eulalie.instance-data
  (:require [eulalie.impl.util :as util :refer [update-when]]
            [#? (:clj clj-time.format :cljs cljs-time.format) :as time.format]
            [#? (:clj clj-time.coerce :cljs cljs-time.coerce) :as time.coerce]
            [clojure.set    :as set]
            [clojure.string :as str]
            [promesa.core   :as p]
            [kvlt.core      :as kvlt]
            [camel-snake-kebab.core   :as csk]
            [camel-snake-kebab.extras :as csk-extras]))

(def ^:private ec2-metadata-host "169.254.169.254")

(defn retrieve!
  "(retrieve! [:latest :dynamic :instance-identity :document] {:parse-json? true})"
  [path &
   [{:keys [host parse-json?] :or {host ec2-metadata-host}}]]
  (let [path (cond-> path (not (coll? path)) vector)
        url  (str "http://" host ":80/" (str/join "/" (map name path)))]
    (p/then
     (kvlt/request! {:url url :as (if parse-json? :json :string)})
     (fn [resp]
       (cond->> (resp :body)
         parse-json? (csk-extras/transform-keys csk/->kebab-case-keyword))))))

#?(:clj (def retrieve!! (comp deref retrieve!)))

(defn metadata!
  "(metadata! [:iam :security-credentials])"
  [path & [args]]
  (retrieve! (flatten (conj [:latest :meta-data] path)) args))
#?(:clj (def metadata!! (comp deref metadata!)))

(defn instance-identity!
  "(instance-identity! :document {:parse-json? true})"
  [path & [args]]
  (retrieve!
   (flatten (conj [:latest :dynamic :instance-identity] path))
   args))
#?(:clj (def instance-identity!! (comp deref instance-identity!)))

(defn identity-key! [k & [args]]
  (p/then (instance-identity! :document (assoc args :parse-json? true))
    (fn [m]
      (get m (keyword k)))))

#?(:clj (def identity-key!! (comp deref identity-key!)))

(defn default-iam-role! [& [args]]
  (p/then (metadata! [:iam :security-credentials] args)
    (fn [s]
      (some-> s (util/to-first-match "\n")))))

#?(:clj (def default-iam-role!! (comp deref default-iam-role!)))

(let [seconds-formatter (time.format/formatters :date-time-no-ms)]
  (defn from-iso-seconds [x]
    (time.coerce/to-long (time.format/parse seconds-formatter x))))

(defn- tidy-iam-creds [{:keys [expiration] :as m}]
  (let [m (set/rename-keys m {:access-key-id     :access-key
                              :secret-access-key :secret-key})]
    (update-when m :expiration from-iso-seconds)))

(defn iam-credentials! [& [role]]
  (let [pending-role (if role
                       (p/resolved role)
                       (default-iam-role!))
        cont (fn [role]
               (-> (metadata!
                    [:iam :security-credentials (name role)]
                    {:parse-json? true})
                   (p/then tidy-iam-creds)))]
    (if role
      (cont role)
      (p/then (default-iam-role!) cont))))
#?(:clj (def iam-credentials!! (comp deref iam-credentials!)))
