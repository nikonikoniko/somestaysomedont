(ns gen.core
  (:require [clojure.core.async :as async]
            [selmer.parser :as parser]
            [gen.contentful-api :as contentful]
            [gen.utils :as utils]))

;;
;;  short introduction to selmer
;;
;;
;; easily render a string:
(parser/render "Hello {{name}}!" {:name "Yogthos"})

(selmer.parser/set-resource-path! "..")
;; also render the resting file:
(parser/render-file "templates/test.html" {:var 4 :items [1 2 3]})

(parser/known-variables "template string will return {{name}} as {:name}")

;; wrote a little helper to do it from filename
(utils/known-file-vars "templates/test.html")
;; should return #{:var :items}

(defn content [] {:site-title "Some Stay Some Don't"
                  :gallery-images (contentful/gallery-images)})

(defn templates [] (utils/filenames "templates"))

(defn build-site []
  (let [templates (templates)
        content (content)]
    (map (fn [template]
           (let [file-name (str "./_dist/" template)
                 file-content (parser/render-file template content)]
             (clojure.java.io/make-parents file-name)
             (spit file-name file-content ))) templates)))

(println (build-site))

(defn -main []
  (println "running main function main function"))
