(defproject io.nervous/eulalie "0.2.1-SNAPSHOT"
  :description "Asynchronous, pure-Clojure AWS client"
  :url "https://github.com/nervous-systems/eulalie"
  :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :scm {:name "git" :url "https://github.com/nervous-systems/eulalie"}
  :deploy-repositories [["clojars" {:creds :gpg}]]
  :signing {:gpg-key "moe@nervous.io"}
  :global-vars {*warn-on-reflection* true}
  :source-paths ["src"]
  :dependencies
  [[org.clojure/clojure        "1.6.0"]
   [org.clojure/core.async     "0.1.346.0-17112a-alpha"]
   [org.clojure/tools.logging  "0.3.1"]
   [org.clojure/algo.generic   "0.1.2"]

   [camel-snake-kebab           "0.2.5"]

   [org.slf4j/slf4j-log4j12    "1.7.5"]
   [log4j/log4j                "1.2.17"
    :exclusions [javax.mail/mail
                 javax.jms/jms
                 com.sun.jmdk/jmxtools
                 com.sun.jmx/jmxri]]

   [http-kit                   "2.1.18"]
   [com.cemerick/url           "0.1.1"]
   [cheshire                   "5.3.1"]
   [digest                     "1.4.4"]
   [clj-time                   "0.9.0"]]
  :exclusions [[org.clojure/clojure]]

  :profiles {:dev
             {:dependencies
              [[com.amazonaws/aws-java-sdk "1.9.3"
                :exclusions [joda-time
                             commons-logging
                             fasterxml.jackson.core/jackson-core]]
               [org.slf4j/jcl-over-slf4j   "1.7.7"]]
              :source-paths ["src" "test"]
              :aot [eulalie.TestableAWS4Signer]}})
