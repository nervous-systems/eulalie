(ns eulalie.impl.doc
  (:require [clojure.string :as str]
            #?(:clj  [clojure.pprint :as pprint]
               :cljs [cljs.pprint :as pprint]))
  #?(:cljs (:require-macros [eulalie.impl.doc])))

(defn pprint-str [x]
  (str/trimr (with-out-str (pprint/pprint x))))

(defn doc-examples! [vvar examples]
  (alter-meta!
   vvar update :doc str
   "\n\n```clojure\n"
   (str/join
    "\n\n"
    (for [[before after] examples]
      (cond-> (pprint-str before)
        after (str "\n  =>\n" (pprint-str after)))))
   "\n```"))

#? (:clj
    (defmacro with-doc-examples! [vvar & examples]
      `(doc-examples! #'~vvar (quote ~examples))))
