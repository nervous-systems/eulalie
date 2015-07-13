(ns eulalie.test.common)

(def creds
  #? (:clj
      {:access-key (get (System/getenv) "AWS_ACCESS_KEY")
       :secret-key (get (System/getenv) "AWS_SECRET_KEY")}
      :cljs
      {:access-key (aget js/process "env" "AWS_ACCESS_KEY")
       :secret-key (aget js/process "env" "AWS_SECRET_KEY")}))
