(ns somestaysomedont.contentful
  (:require [clojure.core.async :as async]
            [selmer.parser :as parser]
            ;;[image-resizer.format :as format]
            ;;[image-resizer.core :as resizer]
            [clj-img-resize.core :as i]
            [clj-contentful :as contentful-api]
            [clojure.java.io :as io]
            [contentql.core :as contentql])
  )

(def config ["dthgcszv3eza"
             "mCnhOr7zovF3PetNrVjRC6aKpok6Fl90MCofupbvmdw"
             "master"])

(defn get-asset [id] (contentful-api/asset config id))

(defn resize-image [path]
  (println "resizing! " path)
  (-> (io/file path) ;;Any image
      (i/scale-image-to-dimension-limit 800 800 "jpeg") ;; Width, Height, and output file format such as jpeg/gif/png
      (io/copy (io/file (str path))) ;; Write the resulting stream into a file
      ))


(defn download-asset [url]
  (println "downloading! " url)
  (let [unique (gensym)
        from   (str "https:" url)
        to     (str "./sites/somestaysomedont/_dist/static/" unique)
        link   (str "/static/" unique)]
    (io/make-parents to)
    (with-open [in  (io/input-stream from)
                out (io/output-stream to)]
      (io/copy in out))
    (resize-image to)
    link))

(def map-image
  (fn [g]
    {:id        (-> g :sys :id)
     :subtext   (-> g :fields :subtext :content first :content first :value)
     :image-url (-> g :fields :image :sys :id get-asset :fields :file :url download-asset)}))

(defn get_images []
  (let [images (contentful-api/entries config {:content_type "galleryImage"})]
    images))

(defn gallery-images []
  (->> (get_images)
       (map map-image)
       doall))
