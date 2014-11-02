(ns saccade.components.bitmap
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [saccade.components.helpers :refer [instrument]]
            [saccade.canvashelpers :refer [clear-canvas]]))

(defn bitmap-width [bitmap]
  (count bitmap))

(defn bitmap-height [bitmap]
  (count (first bitmap)))

(def world-canvas-ref "world-canvas")

(defn paint-world [{:keys [bitmap width-px height-px]} owner]
  (let [ctx (.getContext (om/get-node owner world-canvas-ref) "2d")
        wi (bitmap-width bitmap)
        hi (bitmap-height bitmap)
        wpcell (/ width-px wi)
        hpcell (/ height-px hi)]
    (clear-canvas ctx)
    (set! (.-fillStyle ctx) "black")
    (set! (.-strokeStyle ctx) "#2E7DD1")
    (set! (.-lineWidth ctx) 1)
    (doseq [xi (range wi)
            yi (range hi)
            :let [xp (* xi wpcell)
                  yp (* yi hpcell)]]
      ;; Paint the dot, if applicable.
      (when (= 1 (-> bitmap (nth xi) (nth yi)))
        (.fillRect ctx xp yp wpcell hpcell))
      ;; Paint the grid.
      (.strokeRect ctx xp yp wpcell hpcell))))

(def bitmap-component
  (instrument
   (fn bitmap-component [world owner]
     (reify
       om/IDidMount
       (did-mount [_]
         (paint-world world owner))

       om/IDidUpdate
       (did-update [_ prev-props prev-state]
         (paint-world world owner))

       om/IRenderState
       (render-state [_ {:keys [style]}]
         (dom/canvas #js {:ref world-canvas-ref :width (:width-px world)
                          :height (:height-px world)
                          :style (clj->js style)}))))))
