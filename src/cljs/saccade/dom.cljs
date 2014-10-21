(ns saccade.dom
  (:require [goog.dom :as dom]))

(defn set-style [element kvp]
  (let [style (.-style element)]
    (doseq [[k v] kvp]
      (aset style k v))))

(defn create-styled-dom [tagname style opt-attributes & args]
  (let [el (apply dom/createDom tagname opt-attributes args)]
    (set-style el style)
    el))
