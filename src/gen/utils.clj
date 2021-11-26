(ns gen.utils
  (:require [clojure.core.async :as async]
            [selmer.parser :as parser]))

(defn known-file-vars [filename] (#'parser/parse-variables (:all-tags (meta (parser/parse parser/parse-file filename {})))))

(defn filenames [dir] (map #(.getPath %)
                           (file-seq (clojure.java.io/file dir))))

(defn filenames [dir] (->> dir
                           clojure.java.io/file
                           file-seq
                           (filter #(.isFile %))
                           (map #(.getPath %))))
