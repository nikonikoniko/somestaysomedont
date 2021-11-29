(ns gen.contentful-api
  (:require [clojure.core.async :as async]
            [selmer.parser :as parser]
            [clj-contentful :as contentful]
            [contentql.core :as contentql]))

(def config ["dthgcszv3eza"
             "mCnhOr7zovF3PetNrVjRC6aKpok6Fl90MCofupbvmdw"
             "master"])

(defn get-asset [id] (contentful/asset config id))

(def map-image
  (fn [g]
    {:id          (-> g :sys :id)
     :subtext     (-> g :fields :subtext :content first :content first :value)
     :image-url   (-> g :fields :image :sys :id get-asset :fields :file :url)}))

(defn get_images []
  (contentful/entries config {:content_type "galleryImage"}))

(defn gallery-images []
  (->>  (get_images)
       (map map-image)))
