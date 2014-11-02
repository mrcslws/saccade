(ns saccade.components.conductor
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [saccade.components.helpers :refer [instrument chkcurs]]
            [saccade.components.bitmap :refer [bitmap-width bitmap-height bitmap-component]]
            [saccade.components.lens :refer [lens-component]]))

(def conductor-component
  (instrument
   (fn conductor-component [app owner]
     (let [world (chkcurs (:world app))
           observer (chkcurs (:observer app))]
       (reify
         om/IRenderState
         (render-state [_ {:keys [logchan]}]
           (dom/div #js {:position "relative"
                         :style #js {:width (:width-px world)
                                     :height (:height-px world)}}
                    (om/build bitmap-component world
                              {:init-state
                               {:style {:position "absolute"
                                        :left 0 :top 0 :zIndex 0}}})
                    (om/build lens-component {:world world :observer observer}
                              {:init-state
                               {:logchan logchan
                                :style {:position "absolute"
                                        :left 0 :top 0 :zIndex 1}}}))))))))
