(ns saccade.representations)

(def cellwidth 10)

(defn create-svg-element [tagname]
  (.createElementNS js/document "http://www.w3.org/2000/svg" tagname))

(defn representation-element [arr]
  (let [svg (.importNode js/document (create-svg-element "svg"))
	cols (count arr)
	rows (if (coll? (first arr))
	       (count (first arr))
	       1)]
    (doseq [[k v] {"width" (* cols cellwidth)
		   "height" (* rows cellwidth)}]
      (.setAttribute svg k v))
    (doseq [i (range cols)
					;j (range rows)
	    :let [fill (if (zero? (nth arr i))
			 "white" "red")
		  rect (create-svg-element "rect")
		  x (* i cellwidth)
		  y 0]]
      (doseq [[k v] {"width" cellwidth "height" cellwidth "x" x "y" y "fill" fill}]
	(.setAttribute rect k v)
	(.appendChild svg rect)))
    svg))

;;(representation-element [1 2 3])

;;(.appendChild js/document.body (representation-element [1 2 3]))
