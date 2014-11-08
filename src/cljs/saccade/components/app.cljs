(ns saccade.components.app
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [put! chan mult]]
            [saccade.htm.cloud :refer [cloud-htm-bridge]]
            [saccade.components.helpers :refer [log-lifecycle]]
            [saccade.components.conductor :refer [->conductor-component]]
            [saccade.components.sdrjournal :refer [->sdrjournal-component]]))

(defcomponent app-component [app owner]
  (:mixins log-lifecycle)
  (init-state
   [_]
   (let [to-mult (chan)]
     {:teardown-in to-mult
      :teardown (mult to-mult)}))

  (will-unmount
   [_]
   (put! (om/get-state owner :teardown-in) :destroy-everything))

  (render-state
   [_ {:keys [teardown]}]
   (let [[commands sdrs] (cloud-htm-bridge (:htm-bridge app) teardown)]
     (dom/div nil
              (->conductor-component (select-keys app [:world :lens :view])
                                     {:init-state {:command-channel commands}})
              (->sdrjournal-component (select-keys app [:sdr-journal])
                                      {:init-state {:sdr-channel sdrs}})))))
