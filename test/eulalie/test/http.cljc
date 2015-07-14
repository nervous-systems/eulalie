(ns eulalie.test.http
  (:require
    [clojure.walk :as walk]
   [cemerick.url :as url :refer [url]]
   [cemerick.cljs.test]
   [eulalie.test.platform.http :refer [start-local-server!]]
   #?@(:clj
       [[clojure.core.async :as async :refer [>!]]
        [glossop.core :refer [go-catching <?]]]
       :cljs
       [[cljs.core.async :as async :refer [>!]]]))
  #?(:cljs (:require-macros [glossop.macros :refer [go-catching <?]])))


(defn with-local-server [resps bodyf]
  (go-catching
    (let [reqs  (async/chan (count resps))
          resps (cond-> resps (coll? resps) async/to-chan)
          {:keys [port stop!]}
          (<? ((start-local-server!
                (fn [req]
                  (go-catching
                    (>! reqs (walk/keywordize-keys req))
                    (<? resps))))))]
      (try
        (let [result (<? (bodyf {:port port
                                 :url  (url (str "http://localhost:" port))
                                 :reqs reqs}))]
          {:reqs reqs :result result})
        (finally
          (stop!))))))
