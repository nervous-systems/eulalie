(ns eulalie.instance-data
  "Client for EC2's [instance
  metadata](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html)
  service.  Unless otherwise noted, all functions return promises resolving
  either to the described value, or rejected with an error."
  (:require [eulalie.impl.util :as util :refer [update-when]]
            [eulalie.impl.doc  :as doc]
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
  "Low-level key retrieval function.

  `path` is a vector of keywords representing url segments.

  Options are limited to `host` (default: `196.254.169.254`), used to override
  the EC2 metadata service location and `parse-json?` (`false`). As the remote
  service is not careful with content types, this key is used to indicate that
  the result ought to be parsed/transformed as JSON rather than free-form text."
  [path &
   [{:keys [host parse-json?] :or {host ec2-metadata-host}}]]
  (let [path (cond-> path (not (coll? path)) vector)
        url  (str "http://" host ":80/" (str/join "/" (map name path)))]
    (p/then
     (kvlt/request! {:url url :as (if parse-json? :json :string)})
     (fn [resp]
       (cond->> (resp :body)
         parse-json? (csk-extras/transform-keys csk/->kebab-case-keyword))))))

(doc/with-doc-examples! retrieve!
  [(retrieve [:latest :dynamic :instance-identity :document] {:parse-json? true})])

#?(:clj (def ^{:doc "Clojure-only, blocking implementation of [[retrieve!]]"}
          retrieve!! (comp deref retrieve!)))

(defn metadata!
  "Trivial wrapper around [[retrieve!]] which prefixes `path` with
  `[:latest :meta-data]`."
  [path & [args]]
  (retrieve! (flatten (conj [:latest :meta-data] path)) args))
#?(:clj (def ^{:doc "Clojure-only, blocking implementation of [[metadata!]]"}
          metadata!! (comp deref metadata!)))

(doc/with-doc-examples! metadata!
  [(metadata! [:iam :security-credentials])])

(defn instance-identity!
  "Trivial wrapper around [[retrieve!]] which prefixes `path` with
  `[:latest :dynamic :instance-identity]`."
  [path & [args]]
  (retrieve!
   (flatten (conj [:latest :dynamic :instance-identity] path))
   args))
#?(:clj (def ^{:doc "Clojure-only, blocking implementation of [[instance-identity!]]"}
          instance-identity!! (comp deref instance-identity!)))

(doc/with-doc-examples! instance-identity!
  [(instance-identity! :document {:parse-json? true})])

(defn identity-key!
  "Retrieves `k` from the JSON map at
  `[:latest :dynamic :instance-identity :document]`"
  [k & [args]]
  (p/then (instance-identity! :document (assoc args :parse-json? true))
    (fn [m]
      (get m (keyword k)))))

#?(:clj (def ^{:doc "Clojure-only, blocking implementation of [[identity-key!]]"}
          identity-key!! (comp deref identity-key!)))

(defn default-iam-role!
  "Returns the string name of the instance's default IAM role, or `nil`."
  [& [args]]
  (p/then (metadata! [:iam :security-credentials] args)
    (fn [s]
      (some-> s (util/to-first-match "\n")))))

#?(:clj (def ^{:doc "Clojure-only, blocking implementation of [[default-iam-role!]]"}
          default-iam-role!! (comp deref default-iam-role!)))

(let [seconds-formatter (time.format/formatters :date-time-no-ms)]
  (defn- from-iso-seconds [x]
    (time.coerce/to-long (time.format/parse seconds-formatter x))))

(defn- tidy-iam-creds [{:keys [expiration] :as m}]
  (let [m (set/rename-keys m {:access-key-id     :access-key
                              :secret-access-key :secret-key})]
    (update-when m :expiration from-iso-seconds)))

(defn iam-credentials!
  "Returns a promise resolving to a [[eulalie.creds]] map holding the instance's
  IAM credentials for the named `role`, or the default role if not specified. "
  [& [role]]
  (let [cont (fn [role]
               (-> (metadata!
                    [:iam :security-credentials (name role)]
                    {:parse-json? true})
                   (p/then tidy-iam-creds)))]
    (if role
      (cont role)
      (p/then (default-iam-role!) cont))))
#?(:clj (def ^{:doc "Clojure-only, blocking implementation of [[iam-credentials!]]"}
          iam-credentials!! (comp deref iam-credentials!)))
