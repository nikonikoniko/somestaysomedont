(ns elvirapara.build
  (:require [gen.core :as gen]))

(defn config [] {:template-dir "./sites/elvirapara/templates"
                 :output-dir   "./sites/elvirapara/_dist"})

(defn content []
  {})

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

