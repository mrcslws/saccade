(ns saccade.main
  (:require [om.core :as om :include-macros true]
            [cljs.core.async :refer [chan]]
            [saccade.components.app :refer [app-component]]))

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

    :lens {:xi 3 :yi 3 :wi 3 :hi 3
           :server-token nil}

    :view {:wp 500 :hp 500}

    :sdr-journal {}}))

(defn render-loop []
  (om/root app-component app-state
           {:target (.getElementById js/document "app")
            :shared {:sdr-channel (chan)}}))
