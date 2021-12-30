(ns gen.payload
  (:require [clojure.core.async :as a]
            [clojure.spec.alpha :as s]
            [com.rpl.specter :as specter]
            [gen.utils :as utils]
            [gen.log :as log]

            [clojure.string :as string]
            [selmer.parser :as parser]))

;;
;;  config specs
;;
(s/def ::template-dir string?)

(s/def ::config (s/keys :opt-un [::template-dir]))

(s/valid? ::config
          {}) ; false
(s/valid? ::config
          {:template-dir "..."}) ; true

;;
;;  content specs
;;

(s/def ::content map?)

;;
;; output specs
;;
(s/def ::template-file string?)
(s/def ::context map?)

(s/def ::file-name string?)

(s/def ::output-data (s/keys :req-un [::context
                                      ::template-file]))

(s/def ::output (s/map-of ::file-name ::output-data))

(s/valid? ::output {"blog/slug-1.html" {:context       {}
                                        :template-file "blogs/{{slug}}.html"}}) ; true
(s/valid? ::output {3 {:context       {}
                       :template-file "blogs/{{slug}}.html"}}) ; false because key is int


;;
;;  payload spec
;;
(s/def ::payload (s/keys :req-un [::config
                                  ::content
                                  ::output]))

;; utility functions
;;
(defn config [payload] (:config payload))
(defn output [payload] (:output payload))
(defn content [payload] (:content payload))

(defn map-output
  "takes a function and applies it to each item in the output map. will flatten and merge any created output maps together"
  [f payload]
  {:pre  []
   :post (s/valid? ::payload %)}
  (specter/transform [:output] #(->> %
                                     (map (fn [[file-name output-data]] (f file-name output-data)))
                                     flatten
                                     utils/merge-list) payload))

;; (parser/known-variables "{{content..lang}}/{{content..lang..blogs..slug}}")

;; (specter/select [:content specter/MAP-VALS :blogs specter/ALL :slug]
;;                 {:content {:en {:blogs [{:slug "1"}
;;                                         {:slug "2"}
;;                                         {:slug "3"}]}
;;                            :es {:blogs [{:slug "4"}
;;                                         {:slug "5"}
;;                                         {:slug "6"}]}}})
