(ns saccade.components.app
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [saccade.components.helpers :refer [log-lifecycle]]
            [saccade.components.conductor :refer [->conductor-component]]
            [saccade.components.sdrjournal :refer [->sdrjournal-component]]))

(defcomponent app-component [app owner]
  (:mixins log-lifecycle)
  (render
   [_]
   (dom/div nil
            (->conductor-component (select-keys app [:world :lens :view]))
            (->sdrjournal-component (select-keys app [:sdr-journal])))))
