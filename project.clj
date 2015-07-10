(defproject io.nervous/eulalie "0.5.2"
  :description "Asynchronous, pure-Clojure AWS client"
  :url "https://github.com/nervous-systems/eulalie"
  :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :scm {:name "git" :url "https://github.com/nervous-systems/eulalie"}
  :deploy-repositories [["clojars" {:creds :gpg}]]
  :signing {:gpg-key "moe@nervous.io"}
  :global-vars {*warn-on-reflection* true}
  :source-paths ["src" "test"]
  :dependencies
  [[org.clojure/clojure        "1.7.0"]
   [org.clojure/core.async     "0.1.346.0-17112a-alpha"]
   [org.clojure/algo.generic   "0.1.2"]
   [org.clojure/clojurescript  "0.0-3308"]

   [camel-snake-kebab          "0.3.0"]

   [http-kit                   "2.1.18"]
   [com.cemerick/url           "0.1.1"]
   [cheshire                   "5.5.0"]
   [digest                     "1.4.4"]
   [clj-time                   "0.9.0"]
   [base64-clj                 "0.1.1"]

   [ch.qos.logback/logback-classic "1.1.2"]]
  :exclusions [[org.clojure/clojure]]
  :node-dependencies [[source-map-support "0.2.8"]
                      [node-crc "3.3.0"]]
  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-npm "0.5.0"]]

  :profiles {:dev
             {:dependencies
              [[com.amazonaws/aws-java-sdk "1.9.3"
                :exclusions [joda-time
                             commons-logging
                             fasterxml.jackson.core/jackson-core]]
               [org.slf4j/jcl-over-slf4j   "1.7.7"]]
              :source-paths ["src" "test"]
              :aot [eulalie.TestableAWS4Signer]}}

  :cljsbuild
  {:builds [{:id "eulalie"
             :source-paths ["src"]
             :compiler {:output-to "out/eulalie.js"
                        :output-dir "out"
                        :target :nodejs
                        :optimizations :none
                        :source-map true}}]})
