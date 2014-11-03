(ns saccade.components.conductor
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [saccade.components.helpers :refer [instrument]]
            [saccade.components.bitmap :refer [bitmap-component]]
            [saccade.components.lens :refer [lens-component]]))

(def conductor-component
  (instrument
   (fn conductor-component [app owner]
     (reify
       om/IRender
       (render [_]
         (let [{:keys [world world-view lens]} app
               twolayer-style {:position "absolute" :left 0 :top 0}]
           (dom/div #js {:position "relative"
                         :style #js {:width (:wp world-view)
                                     :height (:hp world-view)}}
                    (om/build bitmap-component
                              {:bitmap world :view-config world-view}
                              {:init-state
                               {:style (assoc twolayer-style
                                         :zIndex 0)}})
                    (om/build lens-component
                              {:bitmap world :view-config world-view :lens lens}
                              {:init-state
                               {:style (assoc twolayer-style
                                         :zIndex 1)}}))))))))
