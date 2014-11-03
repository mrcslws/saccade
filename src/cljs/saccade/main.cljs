(ns saccade.main
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan]]
            [saccade.components.conductor :refer [conductor-component]]
            [saccade.components.sdrjournal :refer [sdrjournal-component]])
  (:require-macros [saccade.macros :refer [set-prefixed!]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defonce app-state
  (atom
   {:world [[0 0 0 0 0 0 0 0 0]
            [0 0 0 0 0 0 0 0 0]
            [0 0 0 0 0 0 0 0 0]
            [0 0 0 0 0 0 0 0 0]
            [0 0 0 0 1 0 0 0 0]
            [0 0 0 0 0 1 0 0 0]
            [0 0 0 0 0 0 0 0 0]
            [0 0 0 0 0 0 0 0 0]
            [0 0 0 0 0 0 0 0 0]]
    :world-view {:wp 500 :hp 500}
    :lens {:xi 3 :yi 3 :wi 3 :hi 3
           :server-token nil}
    :sdr-journal {}}))

(defonce sdr-channel (chan))

(defn render []
  (println app-state)
  (om/root conductor-component app-state
           {:target (.getElementById js/document "app")
            :shared {:sdr-channel sdr-channel}})
  (om/root sdrjournal-component app-state
           {:target (.getElementById js/document "log")
            :shared {:sdr-channel sdr-channel}}))
