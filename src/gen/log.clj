(ns gen.log
  (:require [clojure.core.async :as async]
            [selmer.parser :as parser]))


(defn debug [& args] (println args))
