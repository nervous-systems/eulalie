(defproject io.nervous/eulalie-container "0.1.0-SNAPSHOT"
  :description "Asynchronous, pure-Clojure AWS client"
  :profiles    {:dev {:dependencies [[io.nervous/codox-nervous-theme "0.1.0"]
                                     [io.nervous/promesa-check "0.1.0"]]}}
  :modules
  {:inherited
   {:dependencies
    [[org.clojure/clojure       "1.9.0-alpha14"]
     [org.clojure/clojurescript "1.9.293"]]
    :url     "https://github.com/nervous-systems/eulalie"
    :license {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
    :scm     {:name "git" :url "https://github.com/nervous-systems/eulalie"}
    :codox   {:metadata   {:doc/format :markdown}
              :namespaces [#"^eulalie\.(?!impl)"]
              :themes     [:default [:nervous {:nervous/github "https://github.com/nervous-systems/eulalie"}]]}
    :plugins [[lein-npm       "0.6.2"]
              [lein-doo       "0.1.7"]
              [lein-codox     "0.10.2"]
              [lein-cljsbuild "1.1.5"]]
    :cljsbuild
    {:builds {:generic {:source-paths ["src" "test"]
                        :compiler {:output-to     "target/generic/eulalie-test.js"
                                   :output-dir    "target/generic"
                                   :optimizations :none
                                   :language-in   :ecmascript5
                                   :language-out  :ecmascript5
                                   :pretty-print  true
                                   :main          eulalie.test.runner}}
              :node    {:source-paths ["src" "test"]
                        :compiler {:output-to     "target/node/eulalie-test.js"
                                   :output-dir    "target/node"
                                   :target        :nodejs
                                   :optimizations :none
                                   :main          eulalie.test.runner}}}}}}
  :plugins [[lein-modules "0.3.11"]])
