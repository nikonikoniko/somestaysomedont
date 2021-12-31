(ns somestaysomedont.build
  (:require [gen.core :as gen]
            [somestaysomedont.contentful :as contentful]))

(defn config [] {:template-dir "./sites/somestaysomedont/templates"
                 :output-dir   "./sites/somestaysomedont/_dist"})

(defn content []
  (println "gathering content!")
  {:site-title     "Some Stay Some Don't"
   :gallery-images (contentful/gallery-images)})

(defn build-site [config content]
  (-> {:config  config
       :content content
       :output  {}}
      gen/->load-templates
      gen/->apply-content-context
      gen/->expand-templates
      gen/->write-files!))

(defn build-this-website [] (build-site (config) (content)))

(defn -main []
  (build-this-website)
  (println "running main function main function"))

