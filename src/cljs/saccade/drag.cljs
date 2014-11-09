(ns saccade.drag
  (:require
   [cljs.core.async :refer [<! put! chan alts! take!]]
   [goog.events :as events])
  (:require-macros [saccade.macros :refer [drain!]]
                   [cljs.core.async.macros :refer [go-loop alt!]]))

(defn listen [el type]
  (let [port (chan)
        eventkey (events/listen el type #(put! port %1))]
    [eventkey port]))

(defn watch [mousedown start progress finished]
  (go-loop []
    (when-let [downevt (<! mousedown)]
      (when (= (.-button downevt) 0)
        (put! start :started)

        (let [[kmousemove moves] (listen js/window "mousemove")
              [kmouseup ups] (listen js/window "mouseup")]
          (loop []
            (let [[evt port] (alts! [moves ups])
                  d [(- (.-clientX evt)
                        (.-clientX downevt))
                     (- (.-clientY evt)
                        (.-clientY downevt))]]
              (if (= port moves)
                (do
                  (put! progress d)
                  (recur))
                (do
                  (put! finished d)
                  (events/unlistenByKey kmousemove)
                  (events/unlistenByKey kmouseup))))))

        ;; In obscure cases (e.g. javascript breakpoints)
        ;; there are stale mousedowns sitting in the queue.
        (drain! mousedown))
      (recur))))
