(ns gen.core
  (:require [clojure.core.async :as async]
            [clojure.string :as string]
            [selmer.parser :as parser]
            [clojure.java.io :as io]
            [clojure.test :as test]
            [gen.contentful-api :as contentful]
            [gen.log :as log]
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


;; a build chain function takes, as args,
;;   [content,  content, config]
;;    and returns
;;   [content,  content, config]
;;

;;   output format
;;     {"local/file/path.html" {:template-file "local/file/path.html"
;;                              :context {}}
;;      "another/file/to/write.html" {:template-file "generic-template.html"
;;                                    :context {}}}
;;

(defn ->load-templates
  "loads template names from (:template-dir config) and applies filenames to output"
  {:test (fn [] (test/is (= (->load-templates [{:template-dir "./testing-templates"} {} {}]) ;; INPUT
                            ;; OUTPUT:
                            [{:template-dir "./testing-templates"} {}
                             '({"blog/{{blogs.slug}}.html" {:template-file "blog/{{blogs.slug}}.html",
                                                            :context {}}}
                               {"test.html" {:template-file "test.html", :context {}}}
                               {"index.html" {:template-file "index.html", :context {}}})]
                            )))}
  [[config, content, output]]
  (log/debug "calling ->load-templates")
  (let [{template-dir :template-dir} config
        templates                    (utils/filenames template-dir)
        output                       (-> (map (fn [^String template]
                                                (log/debug template)
                                                {template {:template-file template
                                                           :context content}}) templates)
                                         utils/merge-list) ]
    [config, content, output])) ;; TODO output get's replaced, need to merge

(defn ->apply-content-context
  "applies content to template context, merging it in"
  [[config content output]]
  {:test (fn [] (test/is (= (->apply-content-context [{}
                                                      {:some "content" :blogs [{:slug "first-post"}]}
                                                      {"test.html" {:template-file "test.html"
                                                                    :context {}}}])
                            [{}
                             {:some "content", :blogs [{:slug "first-post"}]}
                             {"test.html" {:template-file "test.html",
                                           :context {:some "content", :blogs [{:slug "first-post"}]}}}]
                            )))}
  (let [results (->> output
                     (map (fn [[k v]] {k (assoc-in v [:context] content)}))
                     flatten
                     utils/merge-list)]
    [config content results]))

(defn ->expand-templates
  "expand any jinja in the template names and include the local variables.  Will apply found vectors in context with a -item postfix i.e blogs-item"
  {:test (fn [] (test/is (= (->expand-templates [{} ; no config needed
                                                 {:blogs [{:slug "post-1-slug"} {:slug "post-2-slug"}]} ; the content has some blogs
                                                 ;; you should see any filenames that have jinja templates in them be expanded:
                                                 {"blog/{{blogs.slug}}.html"
                                                  {:template-file "blog/{{blogs.slug}}.html"
                                                   :context {}}
                                                  ;; and the other ones get left alone
                                                  "test.html"
                                                  {:template-file "test.html"
                                                   :context {}}}])
                            [{} ;; config is unchanged
                             {:blogs [{:slug "post-1-slug"} {:slug "post-2-slug"}]} ;; content is unchanged
                             ;; filenames have been expanced
                             {"blog/post-1-slug.html" {:template-file "blog/{{blogs.slug}}.html", :context {:blogs-item {:slug "post-1-slug"}}},
                              "blog/post-2-slug.html" {:template-file "blog/{{blogs.slug}}.html", :context {:blogs-item {:slug "post-2-slug"}}},
                              "test.html" {:template-file "test.html", :context {}}}]
                            )))}
  [[config content output]]
  (log/debug "calling ->expand-templates")
  (let [results (->> output
                     (map (fn [[file-name file-data]]
                            (let [known-vars (parser/known-variables file-name)]
                              (if (empty? known-vars)
                                {file-name file-data} ;; if there is nothing to do, expand as is
                                ;; else, let's expand the file name
                                (do (log/debug "expanding " file-name)
                                    (log/debug known-vars)
                                    ;; get the key of the known vars out of the content map
                                    (->> known-vars
                                         (map (fn [known-var-key]
                                                (log/debug "looking for " known-var-key " in content")
                                                ;; TODO split known var by `.` i.e. :blogs.2021 -> :blogs :2021
                                                (let [val (known-var-key content)]
                                                  (cond (or (seq? val)
                                                            (list? val)
                                                            (vector? val)) (do (log/debug "found vector, applying each to template")
                                                                               (->> val
                                                                                    (map (fn [item]
                                                                                           (let [filename-context {known-var-key item}
                                                                                                 extra-context {(keyword (str (name known-var-key) "-item")) item}
                                                                                                 existing-context (:context file-data)
                                                                                                 new-context (merge existing-context extra-context)
                                                                                                 parsed-filename (parser/render file-name filename-context)]
                                                                                             {parsed-filename (assoc file-data :context new-context)})))
                                                                                    flatten
                                                                                    utils/merge-list))
                                                        :else (log/debug "don't know how to expand " val)))))
                                         flatten
                                         utils/merge-list))))))
                     flatten
                     utils/merge-list)]
    [config content results]))

(defn ->write-files!
  "writes the output files which should be in format"
  {:test (fn []
           (let [temp (gensym)] ;; temporary value to test for
             (->write-files! [{} ;; config ignored
                                  {} ;; content ignored
                                  {"tests/test.html" {:template-file "test.html"
                                                      :context {:a temp}}}])
             (test/is (= (slurp "./_dist/tests/test.html")
                         (str temp "\n")))) ;; test if the value was written to file
            )}
  [[config content output]]
  (log/debug "calling ->write-templates!")
  (->> output
       (map (fn [[file-name {context :context
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
                          (string/ends-with? file-name ".woff"))
                      (with-open [in (io/input-stream (str (:template-dir config) "/" template-file))
                                  out (io/output-stream out-file-name)]
                        (io/copy in out)))
                      )
                ))
       doall)
  ;; return unchanged from side effect
  [config content output])

(test/run-tests)

(defn config [] {:template-dir "./templates"})

(defn content [] {:site-title "Some Stay Some Don't"
                  :gallery-images (contentful/gallery-images)})

(defn build-site [config content]
  (-> [config content {}]
      ->load-templates
      ->apply-content-context
      ->expand-templates
      ->write-files!))

(defn build-this-website [] (build-site (config) (content)))

(defn -main []
  (build-this-website)
  (println "running main function main function"))
