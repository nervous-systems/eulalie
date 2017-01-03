(defproject io.nervous/eulalie-core "0.1.0-SNAPSHOT"
  :description  "Asynchronous, pure-Clojure AWS client"
  :dependencies [[io.nervous/glossop         "0.2.1"]
                 [io.nervous/kvlt            "0.1.4"]

                 [camel-snake-kebab          "0.4.0"]

                 [com.cemerick/url           "0.1.1"]
                 [digest                     "1.4.4"]
                 [clj-time                   "0.12.2"]
                 [base64-clj                 "0.1.1"]
                 [cheshire                   "5.5.0"]

                 [com.andrewmcveigh/cljs-time    "0.4.0"]
                 [io.nervous/cljs-nodejs-externs "0.1.0"]]
  :plugins      [[lein-modules "0.3.11"]]
  :codox         {:source-uri ~(str "https://github.com/nervous-systems/eulalie/"
                                    "eulalie-core/blob/master/{filepath}#L{line}")})
