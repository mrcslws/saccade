(ns saccade.components.helpers
  (:require [om.core :as om :include-macros true]))

(defn chkcurs [v]
  (if (om/cursor? v)
    v
    (throw (js/Error. (str v " is not a cursor!")))))


(def log? true)
(defn instrument [actual-view]
  (fn instrumented-view [state owner]
    (let [component-name (.-name actual-view)
          log (fn [& args] (when log? (apply println component-name args)))
          faker (reify)
          actual (actual-view state owner)]
      (log)
      (when (satisfies? om/IInitState actual)
        (specify! faker om/IInitState (init-state [_]
                                        (log "init-state")
                                        (om/init-state actual))))
      (when (satisfies? om/IRenderState actual)
        (specify! faker om/IRenderState (render-state [_ state]
                                          (log "render-state")
                                          (om/render-state actual state))))
      (when (satisfies? om/IRender actual)
        (specify! faker om/IRender (render [_]
                                     (log "render")
                                     (om/render actual))))
      (when (satisfies? om/IWillMount actual)
        (specify! faker om/IWillMount (will-mount [_]
                                        (log "will-mount")
                                        (om/will-mount actual))))
      (when (satisfies? om/IWillUnmount actual)
        (specify! faker om/IWillUnmount (will-unmount [_]
                                        (log "will-unmount")
                                        (om/will-unmount actual))))
      (when (satisfies? om/IDidMount actual)
        (specify! faker om/IDidMount (did-mount [_]
                                       (log "did-mount")
                                       (om/did-mount actual))))
      (when (satisfies? om/IDidUpdate actual)
        (specify! faker om/IDidUpdate (did-update [_ prev-props prev-state]
                                        (log "did-update")
                                        (om/did-update actual prev-props
                                                       prev-state))))

      faker)))
