(defproject io.nervous/eulalie-services "0.1.0-SNAPSHOT"
  :description  "FIXME: write description"
  :url          "https://github.com/nervous-systems/eulalie/eulalie-services"
  :license      {:name "Unlicense" :url "http://unlicense.org/UNLICENSE"}
  :dependencies [[org.clojure/clojure        "1.9.0-alpha14"]
                 [org.clojure/clojurescript  "1.9.293"]
                 [io.nervous/eulalie-core    "0.1.0-SNAPSHOT"]]
  :profiles     {:dev {:dependencies [[io.nervous/promesa-check "0.1.0-SNAPSHOT"]]}})
