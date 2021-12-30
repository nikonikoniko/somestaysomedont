(ns gen.contentful-api
  (:require [clojure.core.async :as async]
            [selmer.parser :as parser]
            ;;[image-resizer.format :as format]
            ;;[image-resizer.core :as resizer]
            [clj-img-resize.core :as i]
            [clj-contentful :as contentful]
            [clojure.java.io :as io]
            [contentql.core :as contentql])
  (:import javax.imageio.ImageIO))

(def config ["dthgcszv3eza"
             "mCnhOr7zovF3PetNrVjRC6aKpok6Fl90MCofupbvmdw"
             "master"])

(defn get-asset [id] (contentful/asset config id))

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
        to     (str "_dist/static/" unique)
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
(contentful/entries config {:content_type "galleryImage"}))

(defn gallery-images []
(->> (get_images)
     (map map-image)))
