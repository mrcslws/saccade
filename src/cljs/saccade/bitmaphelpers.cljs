(ns saccade.bitmaphelpers)

(defn width [bitmap]
  (count bitmap))

(defn height [bitmap]
  (count (first bitmap)))

(defn onto-px [bitmap view]
  (let [{:keys [wp hp]} view
        wi (width bitmap)
        hi (height bitmap)]
    (assoc view :wi wi :hi hi :wpcell (/ wp wi) :hpcell (/ hp hi))))
