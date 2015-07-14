(ns eulalie.platform.xml
  (:require [cljs.nodejs :as nodejs]
            [clojure.walk :as walk]
            [glossop.core :as glossop]
            [camel-snake-kebab.core :as csk]))

(def parser
  (.Parser
   (nodejs/require "xml2js")
   (clj->js {:ignoreAttrs true
             :explicitChildren true
             :childkey "content"
             :explicitArray true
             :preserveChildrenOrder true
             :async false
             :charsAsChildren true
             :explicitRoot false})))

(defn string->xml-map [s]
  (let [result (atom nil)]
    (.parseString parser s #(reset! result (or %2 %1)))
    (let [result (glossop/throw-err @result)]
      (walk/postwalk
       (fn [x]
         (if (and (map? x) (x "#name"))
           (if (= "__text__" (x "#name"))
             (x "_")
             {(csk/->kebab-case-keyword (x "#name")) (x "content")})
           x))
       (js->clj result)))))

