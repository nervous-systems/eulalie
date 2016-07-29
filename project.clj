(defproject io.nervous/eulalie "0.7.0-SNAPSHOT"
  :description "Asynchronous, pure-Clojure AWS client"
  :url "https://github.com/nervous-systems/eulalie"
  :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :scm {:name "git" :url "https://github.com/nervous-systems/eulalie"}
  :global-vars {*warn-on-reflection* true}
  :source-paths ["src"]
  :dependencies
  [[org.clojure/clojure        "1.8.0"]
   [org.clojure/clojurescript  "1.8.34"]
   [org.clojure/core.async     "0.2.374"]

   [io.nervous/glossop         "0.2.1"]
   [prismatic/plumbing         "0.4.4"]

   [camel-snake-kebab          "0.3.2"]

   [http-kit                   "2.1.18"]
   [com.cemerick/url           "0.1.1"]
   [cheshire                   "5.5.0"]
   [digest                     "1.4.4"]
   [clj-time                   "0.11.0"]
   [base64-clj                 "0.1.1"]

   [com.andrewmcveigh/cljs-time    "0.4.0"]
   [io.nervous/cljs-nodejs-externs "0.1.0"]]
  :exclusions [[org.clojure/clojure]]

  :node-dependencies [[source-map-support "0.2.8"]
                      [buffer-crc32       "0.2.5"]
                      [xml2js             "0.4.9"]]

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-npm       "0.5.0"]
            [lein-doo       "0.1.7-SNAPSHOT"]]

  :test-selectors {:default (complement :ec2)
                   :ec2 :ec2
                   :local #(not (or (:ec2 %) (:aws %)))}

  :cljsbuild
  {:builds [{:id "main"
             :source-paths ["src"]
             :compiler {:output-to     "eulalie.js"
                        :target        :nodejs
                        :hashbang      false
                        :optimizations :none
                        :source-map    true}}
            {:id "test-none"
             :source-paths ["src" "test"]
             :notify-command ["node" "target/test-none/eulalie-test.js"]
             :compiler {:output-to "target/test-none/eulalie-test.js"
                        :output-dir "target/test-none"
                        :target        :nodejs
                        :optimizations :none
                        :main          eulalie.test.runner}}
            {:id "test-advanced"
             :source-paths ["src" "test"]
             :compiler {:output-to "target/test-advanced/eulalie-test.js"
                        :output-dir "target/test-advanced"
                        :target        :nodejs
                        :optimizations :advanced
                        :main          eulalie.test.runner}}]}
  :profiles {:dev {:source-paths ["src" "test"]}})
