(defproject io.nervous/eulalie-dynamo "0.1.0-SNAPSHOT"
  :description  "Eulalie's DynamoDB service implementation"
  :dependencies [[io.nervous/eulalie-service-util :version]]
  :profiles     {:dev {:dependencies [[io.nervous/promesa-check "0.1.0-SNAPSHOT"]]}}
  :plugins      [[lein-modules "0.3.11"]])
