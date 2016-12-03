(ns eulalie.impl.sign
  (:require [eulalie.impl.util :as util]
            [clojure.string :as str]))

(def DEFAULT-REGION "us-east-1")

(defn sanitize-creds [m]
  (into {}
    (for [[k v] m]
      [k (cond-> v (string? v) str/trim)])))

(defn newline-join [& rest]
  (str/join "\n" rest))

(defn default-port? [{:keys [protocol port]}]
  (or (and (= protocol "http")  (= port 80))
      (and (= protocol "https") (= port 443))))

(defn host-header [{:keys [host port] :as endpoint}]
  (cond-> host (not (default-port? endpoint)) (str ":" port)))

(defn- collapse-whitespace [s]
  (some-> s str (str/replace #"\s+" " ")))

(defn canonical-header [[k v]]
  [(-> k name str/lower-case collapse-whitespace)
   (collapse-whitespace v)])
