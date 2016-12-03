(defproject eulalie-core "0.1.0-SNAPSHOT"
  :description  "Asynchronous, pure-Clojure AWS client"
  :url          "https://github.com/nervous-systems/eulalie"
  :license      {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :scm          {:name "git" :url "https://github.com/nervous-systems/eulalie"}
  :dependencies [[org.clojure/clojure        "1.8.0"]
                 [org.clojure/clojurescript  "1.8.51"]
                 [org.clojure/core.async     "0.2.395"]

                 [io.nervous/glossop         "0.2.1"]
                 [io.nervous/kvlt            "0.1.4"]

                 [camel-snake-kebab          "0.4.0"]

                 [com.cemerick/url           "0.1.1"]
                 [digest                     "1.4.4"]
                 [clj-time                   "0.12.2"]
                 [base64-clj                 "0.1.1"]
                 [cheshire                   "5.5.0"]

                 [com.andrewmcveigh/cljs-time    "0.4.0"]
                 [io.nervous/cljs-nodejs-externs "0.1.0"]]
  :plugins      [[lein-npm "0.6.0"]
                 [lein-doo "0.1.7"]]
  :cljsbuild
  {:builds [{:id "test-none"
             :source-paths ["src" "test"]
             :compiler {:output-to     "target/test-none/eulalie-test.js"
                        :output-dir    "target/test-none"
                        :target        :nodejs
                        :optimizations :none
                        :main          eulalie.runner}}]})
