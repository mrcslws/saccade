(ns saccade.components.bitmap
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [saccade.components.helpers :refer [instrument]]
            [saccade.canvashelpers :as canvas]
            [saccade.bitmaphelpers :as bitmap]))

(def bitmap-ref "bitmap")

(defn paint [bitmap {:keys [wp hp]} owner]
  (let [ctx (.getContext (om/get-node owner bitmap-ref) "2d")
        {:keys [wi hi wpcell hpcell]} (bitmap/onto-px bitmap wp hp)]
    (canvas/clear ctx)
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
   (fn bitmap-component [{:keys [bitmap view-config]} owner]
     (reify
       om/IDidMount
       (did-mount [_]
         (paint bitmap view-config owner))

       om/IDidUpdate
       (did-update [_ _ _]
         (paint bitmap view-config owner))

       om/IRenderState
       (render-state [_ {:keys [style]}]
         (dom/canvas #js {:ref bitmap-ref :width (:wp view-config)
                          :height (:hp view-config)
                          :style (clj->js style)}))))))
