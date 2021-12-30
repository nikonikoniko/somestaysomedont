(ns gen.log
  (:require [clojure.core.async :as async]
            [selmer.parser :as parser]))


(defn debug [& args] (println args))
(defn warn [& args] (println args))

(defn tap [x] (debug x) x)
