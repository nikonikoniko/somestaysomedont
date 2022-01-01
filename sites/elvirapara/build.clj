(ns elvirapara.build
  (:require [gen.core :as gen]
            [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [com.rpl.specter :as specter]
            [clj-img-resize.core :as i]
            [clojure.java.io :as io]
            [clj-http.client :as client]))

(defn config [] {:template-dir "./sites/elvirapara/templates"
                 :output-dir   "./sites/elvirapara/_dist"})

(def token "048b2c68d53fd8ffac41d5203f87663f737c31392d80aea5df1c25dac8da539fa4c5397146cb76ba3daa91efc520767f4be60f96fe72d81a63af8171dcf5d0a09c699adb8ffc953b007c951e5944bb0f96e675f8d327ea2dd67462a2eeb63d4b808d91d1ecbe60d215d031521ed7c638e13a5faf3a5917a6c96cad2fe179c4e6")

(defn resize-image [path]
  (println "resizing! " path)
  (-> (io/file path) ;;Any image
      (i/scale-image-to-dimension-limit 800 800 "jpeg") ;; Width, Height, and output file format such as jpeg/gif/png
      (io/copy (io/file (str path))) ;; Write the resulting stream into a file
      ))

(defn download-asset [url]
  (println "downloading!" url)
  (println url)
  (let [unique (gensym)
        from   url
        to     (str "./sites/elvirapara/_dist/static/" unique)
        link   (str "/static/" unique)]
    (io/make-parents to)
    (with-open [in  (io/input-stream from)
                out (io/output-stream to)]
      (io/copy in out))
    (resize-image to)
    link))

(defn convert-gallery-item [g]
  {:id        (-> g :id)
   :subtext   (-> g :attributes :subtext)
   :type      (-> g :attributes :type)
   :order     (-> g :attributes :order)
   :image-url (-> g :attributes :image :data :attributes :url
                  (#(str "https://content.niko.io" %))
                  download-asset)})
(defn convert-gallery-items [gs] (->> (map convert-gallery-item gs)
                                      (group-by :type)))

(defn convert-block [b]
  {(-> b :attributes :name) (-> b :attributes :content)})
(defn convert-blocks [bs] (->> (map convert-block bs)
                               (reduce merge)))

(defn convert-page [b]
  {(-> b :attributes :name) (-> b :attributes :content)})
(defn convert-pages [bs] (->> (map convert-page bs)
                              (reduce merge)))


(defn fetch-gallery-items [] (-> (client/get "https://content.niko.io/api/gallery-items?pagination[pageSize]=100&populate=image"
                                             {:headers {"Authorization" (str "Bearer " token)}
                                              :accept  :json})
                                 :body
                                 (json/parse-string true)
                                 :data))

(defn fetch-blocks [] (-> (client/get "https://content.niko.io/api/blocks?pagination[pageSize]=100"
                                      {:headers {"Authorization" (str "Bearer " token)}
                                       :accept  :json})
                          :body
                          (json/parse-string true)
                          :data))

(defn fetch-pages [] (-> (client/get "https://content.niko.io/api/pages"
                                     {:headers {"Authorization" (str "Bearer " token)}
                                      :accept  :json})
                         :body
                         (json/parse-string true)
                         :data))


(defn content []
  {:title          "Site title"
   :pages          (-> (fetch-pages)
                       (convert-pages))  
   :blocks         (-> (fetch-blocks)
                       (convert-blocks))  
   :gallery-images (-> (fetch-gallery-items)
                       (convert-gallery-items))})

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

