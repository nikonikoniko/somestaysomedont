(ns gen.core
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [selmer.parser :as parser]
            [clojure.java.io :as io]
            [clojure.test :as test]
            [gen.contentful-api :as contentful]
            [gen.log :as log]
            [gen.template :as template]
            [gen.payload :as payload]
            [gen.utils :as utils]))

;;
;;  short introduction to selmer
;;
;;
;; easily render a string:
(parser/render "Hello {{name}}!" {:name "Yogthos"})

;; also render the resting file:
(parser/render-file "../testing-templates/test.html" {:var 4 :items [1 2 3]})

(parser/known-variables "template string will return {{name}} as {:name}")

;; make a filename like this, which will iterate over the variable
(parser/known-variables "{{blogs..slug}}.html")
;; returns #{:blogs.slug}
;;

(def template-expander "{% for blog in blogs %}{{blog.slug}}.html{% endfor %}")

(parser/known-variables template-expander)
;; returns #{:blogs}
;;
(parser/known-variables "some-template.html")

(parser/render template-expander {:blogs [{:slug "post-1-slug"}
                                          {:slug "post-2-slug"}]})

;; wrote a little helper to do it from filename
(utils/known-file-vars "../testing-templates/test.html")
;; should return #{:var :items}



(defn ->load-templates
  "loads template names from (:template-dir config) and applies filenames to output"
  {:test (fn [] (test/is (= (->load-templates [{:template-dir "./testing-templates"} {} {}]) ;; INPUT
                            ;; OUTPUT:
                            [{:template-dir "./testing-templates"} {}
                             '({"blog/{{blogs.slug}}.html" {:template-file "blog/{{blogs.slug}}.html",
                                                            :context       {}}}
                               {"test.html" {:template-file "test.html", :context {}}}
                               {"index.html" {:template-file "index.html", :context {}}})]
                            )))}
  [{:as     data
    config  :config
    content :content
    output  :output}]
  (log/debug "calling ->load-templates")
  (let [{template-dir :template-dir} config
        templates                    (utils/filenames template-dir)
        output                       (-> (map (fn [^String template]
                                                (log/debug template)
                                                {template {:template-file template
                                                           :context       content}}) templates)
                                         utils/merge-list) ]
    {:config  config
     :content content
     :output  output})) ;; TODO output get's replaced, need to merge

(defn ->apply-content-context
  "applies content to template context, merging it in"
  [{:as     data
    config  :config
    content :content
    output  :output}]
  {:test (fn [] (test/is (= (->apply-content-context [{}
                                                      {:some "content" :blogs [{:slug "first-post"}]}
                                                      {"test.html" {:template-file "test.html"
                                                                    :context       {}}}])
                            [{}
                             {:some "content", :blogs [{:slug "first-post"}]}
                             {"test.html" {:template-file "test.html",
                                           :context       {:some "content", :blogs [{:slug "first-post"}]}}}]
                            )))}
  (let [results (->> output
                     (map (fn [[k v]] {k (assoc-in v [:context] content)}))
                     flatten
                     utils/merge-list)]
    {:config  config
     :content content
     :output  results}))



(defn ->expand-templates
  "expand any jinja in the template names and include the local variables.  Will apply found vectors in context with a -item postfix i.e blogs-item"
  {:test (fn [] (test/is (= (->expand-templates {:config  {}                                                     ; no config needed
                                                 :content {:blogs [{:slug "post-1-slug"} {:slug "post-2-slug"}]} ; the content has some blogs
                                                 ;; you should see any filenames that have jinja templates in them be expanded:
                                                 :output  {"blog/{{blogs..EACH.slug}}.html" {:template-file "blog/{{blogs..EACH.slug}}.html"
                                                                                             :context       {:og "keep"}}
                                                           ;; and the other ones get left alone
                                                           "test.html"                      {:template-file "test.html"
                                                                                             :context       {}}}})
                            {:config  {}                                                     ;; config is unchanged
                             :content {:blogs [{:slug "post-1-slug"} {:slug "post-2-slug"}]} ;; content is unchanged
                             ;; filenames have been expanced
                             :output  {"blog/post-1-slug.html" {:template-file "blog/{{blogs..EACH.slug}}.html", :context {:item {:slug "post-1-slug"}
                                                                                                                           :og   "keep"}},
                                       "blog/post-2-slug.html" {:template-file "blog/{{blogs..EACH.slug}}.html", :context {:item {:slug "post-2-slug"}
                                                                                                                           :og   "keep"}},
                                       "test.html"             {:template-file "test.html", :context {}}}})))}
  [{:as     data
    content :content}]
  (log/debug "calling ->expand-templates")
  (->> data
       (payload/map-output (fn [template-name file-data]
                             (->> (template/itemize content template-name)
                                  (map (fn [[file-name new-context]]
                                         {file-name (merge file-data {:context (merge (:context file-data) new-context)})}))
                                  utils/merge-list)
                             )
                           )))

(defn ->write-files!
  "writes the output files which should be in format"
  {:test (fn []
           (let [temp (gensym)] ;; temporary value to test for
             (->write-files! [{} ;; config ignored
                              {} ;; content ignored
                              {"tests/test.html" {:template-file "test.html"
                                                  :context       {:a temp}}}])
             (test/is (= (slurp "./_dist/tests/test.html")
                         (str temp "\n")))) ;; test if the value was written to file
           )}
  [{:as     data
    config  :config
    content :content
    output  :output}]
  (log/debug "calling ->write-templates!")
  (->> output
       (map (fn [[file-name {context       :context
                             template-file :template-file}]]
              (let [out-file-name (str "./_dist/" file-name)]
                (log/debug "! writing file " out-file-name)
                (io/make-parents out-file-name)
                (cond (string/ends-with? file-name ".html") ;; TODO or other things
                      (let [c (parser/render-file template-file context)]
                        (spit out-file-name c))
                      (string/ends-with? file-name ".css")
                      (let [c (parser/render-file template-file context)]
                        (spit out-file-name c))
                      (or (string/ends-with? file-name ".jpg")
                          (string/ends-with? file-name ".ico")
                          (string/ends-with? file-name ".png")
                          (string/ends-with? file-name ".webmanifest")
                          (string/ends-with? file-name ".woff"))
                      (with-open [in  (io/input-stream (str (:template-dir config) "/" template-file))
                                  out (io/output-stream out-file-name)]
                        (io/copy in out)))
                )
              ))
       doall)
  ;; return unchanged from side effect
  data)

                                        ; (test/run-tests)

(defn config [] {:template-dir "./templates"})

(defn content [] {:site-title     "Some Stay Some Don't"
                  :gallery-images (contentful/gallery-images)})

(defn build-site [config content]
  (-> {:config config :content content :output {}}
      ->load-templates
      ->apply-content-context
      ->expand-templates
      ->write-files!))

(defn build-this-website [] (build-site (config) (content)))

(defn -main []
  (build-this-website)
  (println "running main function main function"))
