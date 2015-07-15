(defproject io.nervous/eulalie "1.0.0-SNAPSHOT"
  :description "Asynchronous, pure-Clojure AWS client"
  :url "https://github.com/nervous-systems/eulalie"
  :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :scm {:name "git" :url "https://github.com/nervous-systems/eulalie"}
  :deploy-repositories [["clojars" {:creds :gpg}]]
  :signing {:gpg-key "moe@nervous.io"}
  :global-vars {*warn-on-reflection* true}
  :source-paths ["src"]
  :dependencies
  [[org.clojure/clojure        "1.7.0"]
   [org.clojure/core.async     "0.1.346.0-17112a-alpha"]
   [org.clojure/clojurescript  "0.0-3308"]

   [io.nervous/glossop         "1.0.0-SNAPSHOT"]
   [prismatic/plumbing         "0.4.4"]

   [camel-snake-kebab          "0.3.2"]

   [http-kit                    "2.1.18"]
   [com.cemerick/url            "0.1.1"]
   [cheshire                    "5.5.0"]
   [digest                      "1.4.4"]
   [clj-time                    "0.9.0"]
   [base64-clj                  "0.1.1"]

   [com.andrewmcveigh/cljs-time "0.3.10"]]
  :exclusions [[org.clojure/clojure]]

  :node-dependencies [[source-map-support "0.2.8"] ;; optional
                      [crc                "3.3.0"] ;; response verification, opt.
                      [xml2js             "0.4.9"]] ;; sns/sqs

  :plugins [[lein-cljsbuild "1.0.6"]
            [lein-npm "0.5.0"]
            [com.cemerick/clojurescript.test "0.3.3"]]

  :cljsbuild
  {:builds [{:id "main"
             :source-paths ["src"]
             :compiler {:output-to "eulalie.js"
                        :target :nodejs
                        :hashbang false
                        :optimizations :none
                        :source-map true}}
            {:id "test"
             :source-paths ["src" "test"]
             :compiler {:output-to "target/js-test/test.js"
                        :output-dir "target/js-test"
                        :target :nodejs
                        :hashbang false
                        :source-map true
                        :optimizations :none}}]
   :test-commands {"node" ["node" "runner-none.js" "target/js-test"
                           "target/js-test/test.js"]}}
  :profiles {:dev
             {:repl-options
              {:nrepl-middleware
               [cemerick.piggieback/wrap-cljs-repl]}
              :node-dependencies
              [[portfinder         "0.4.0"]
               [timekeeper         "0.0.5"]]
              :dependencies
              [[com.cemerick/piggieback "0.2.1"]
               [org.clojure/tools.nrepl "0.2.10"]
               [com.cemerick/clojurescript.test "0.3.3"]]
              :source-paths ["src" "test"]}})
