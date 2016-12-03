(ns eulalie.impl.http
  (:require [kvlt.core    :as kvlt]
            [promesa.core :as p]))

(defn request! [req]
  (-> req
      (dissoc :endpoint)
      (assoc :url (str (req :endpoint)))
      kvlt/request!
      (p/catch
        (fn [e]
          (let [{:keys [status] :as e} (ex-data e)]
            (assoc e :eulalie.core/transport-error? (= status 0)))))))
