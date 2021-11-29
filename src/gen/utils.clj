(ns gen.utils
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [selmer.parser :as parser]))

(defn known-file-vars [filename] (#'parser/parse-variables (:all-tags (meta (parser/parse parser/parse-file filename {})))))

(defn filenames [dir] (map #(.getPath %)
                           (file-seq (clojure.java.io/file dir))))

(defn merge-list [list] (apply merge list))

(defn filenames [dir] (->> dir
                           clojure.java.io/file
                           file-seq
                           (filter #(.isFile %))
                           (map #(.getPath %))
                           (map #(string/replace-first % (str dir "/") "")) ;; quick dirty hack to remove the dir itself TODO do this better
                           ))
