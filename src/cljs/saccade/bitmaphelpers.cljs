(ns saccade.bitmaphelpers)

(defn width [bitmap]
  (count bitmap))

(defn height [bitmap]
  (count (first bitmap)))

(defn onto-px [bitmap width-px height-px]
  (let [wi (width bitmap)
        hi (height bitmap)]
    {:wi wi :hi hi
     :wpcell (/ width-px wi)
     :hpcell (/ height-px hi)}))
