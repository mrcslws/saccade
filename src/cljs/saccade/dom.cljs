(ns saccade.dom)

(defn set-style [element kvp]
  (let [style (.-style element)]
    (doseq [[k v] kvp]
      (aset style k v))))
