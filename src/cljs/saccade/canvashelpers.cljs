(ns saccade.canvashelpers)

(defn clear [ctx]
  (.save ctx)
  (.setTransform ctx 1 0 0 1 0 0)
  (.clearRect ctx 0 0 (.. ctx -canvas -width) (.. ctx -canvas -height))
  (.restore ctx))
