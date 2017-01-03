(defproject io.nervous/eulalie-container "0.1.0-SNAPSHOT"
  :description "Asynchronous, pure-Clojure AWS client"
  :modules
  {:inherited {:dependencies
               [[org.clojure/clojure        "1.9.0-alpha14"]
                [org.clojure/clojurescript  "1.9.293"]]
               :url     "https://github.com/nervous-systems/eulalie"
               :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
               :scm     {:name "git" :url "https://github.com/nervous-systems/eulalie"}
               :codox   {:metadata   {:doc/format :markdown}
                         :namespaces [#"^eulalie\.(?!impl)"]}
               :plugins [[lein-npm   "0.6.0"]
                         [lein-doo   "0.1.7"]
                         [lein-codox "0.10.2"]]
               :cljsbuild
               {:builds [{:id "test-none"
                          :source-paths ["src" "test"]
                          :compiler {:output-to     "target/test-none/eulalie-test.js"
                                     :output-dir    "target/test-none"
                                     :target        :nodejs
                                     :optimizations :none
                                     :main          eulalie.runner}}]}}}
  :plugins [[lein-modules "0.3.11"]])
