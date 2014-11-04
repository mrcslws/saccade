(ns saccade.components.conductor
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [saccade.components.bitmap :refer [bitmap-component]]
            [saccade.components.lens :refer [lens-component]]))

(defcomponent conductor-component [app owner]
  (render
   [_]
   (let [{:keys [world lens view]} app
         twolayer-style {:position "absolute" :left 0 :top 0}]
     (dom/div #js {:position "relative"
                   :style #js {:width (:wp view) :height (:hp view)}}
              (om/build bitmap-component
                        {:bitmap world :view view}
                        {:init-state
                         {:style (assoc twolayer-style
                                   :zIndex 0)}})
              (om/build lens-component
                        {:bitmap world :view view :lens lens}
                        {:init-state
                         {:style (assoc twolayer-style
                                   :zIndex 1)}})))))
