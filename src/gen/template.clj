(ns gen.template
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [gen.log :as log]
            [gen.utils :as utils]
            [clojure.test :as test]
            [com.rpl.specter :as specter]
            [selmer.parser :as parser]))


(defn selmer->specter [k]
  {:test (fn [] (test/is (= (selmer->specter (keyword "content.blogs.EACH"))
                            [:content :blogs specter/ALL])))}
  (->> k
       name
       ((fn [x] (string/split x #"\.")))
       (map #(case %
               "EACH" specter/ALL
               (keyword %)))
       vec))

(defn itemize [content template]
  {:test (fn []
           (test/is (= (itemize {:content {:blogs [{:slug "1" :lang "en"}
                                                   {:slug "1" :lang "de"}
                                                   {:slug "2" :lang "en"}
                                                   {:slug "3" :lang "de"}]}}
                                "{{content..blogs..EACH.lang}}/{{content..blogs..EACH.slug}}")
                       {"en/1"  {:item {:slug "1", :lang "en"}}
                        "de/1", {:item {:slug "1", :lang "de"}}
                        "en/2", {:item {:slug "2", :lang "en"}}
                        "de/3", {:item {:slug "3", :lang "de"}}})) 
           (test/is (= (itemize {} "blogs.html")
                       {"blogs.html" {}})))}
  (let [known-vars (parser/known-variables template)
        ;; FOR RIGHT NOW ALLOW ONLY ONE KNOWN VAR IN TEMPLATE
        ;; TODO find behaviour for multple vars or even nested
        v          (first known-vars)]
    (cond (< 1 (count known-vars)) (log/warn "only 1 variable allowed at the moment in template names")
          (= 1 (count known-vars)) (as-> v $
                                     (selmer->specter $)
                                     (specter/select $ content)
                                     (map (fn [x] {(parser/render template {v x})
                                                   {:item x}}) $)
                                     (flatten $)
                                     (utils/merge-list $))
          :else                    {template {}}
          )))
