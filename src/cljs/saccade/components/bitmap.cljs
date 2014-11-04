(ns saccade.components.bitmap
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [saccade.canvashelpers :as canvas]
            [saccade.bitmaphelpers :as bitmap]))

(def bitmap-ref "bitmap")

(defn paint [bitmap view owner]
  (let [ctx (.getContext (om/get-node owner bitmap-ref) "2d")
        {:keys [wpcell hpcell] :as view+} (bitmap/onto-px bitmap view)]
    (canvas/clear ctx)
    (set! (.-fillStyle ctx) "black")
    (set! (.-strokeStyle ctx) "#2E7DD1")
    (set! (.-lineWidth ctx) 1)
    (doseq [xi (range (:wi view+))
            yi (range (:hi view+))
            :let [xp (* xi wpcell)
                  yp (* yi hpcell)]]
      ;; Paint the dot, if applicable.
      (when (= 1 (-> bitmap (nth xi) (nth yi)))
        (.fillRect ctx xp yp wpcell hpcell))
      ;; Paint the grid.
      (.strokeRect ctx xp yp wpcell hpcell))))

(defn bitmap-component [{:keys [bitmap view]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (paint bitmap view owner))

    om/IDidUpdate
    (did-update [_ _ _]
      (paint bitmap view owner))

    om/IRenderState
    (render-state [_ {:keys [style]}]
      (dom/canvas #js {:ref bitmap-ref :width (:wp view) :height (:hp view)
                       :style (clj->js style)}))))
