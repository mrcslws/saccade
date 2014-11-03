(ns saccade.components.lens
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cognitect.transit :as transit]
            [cljs.core.async :refer [<! put! alts! chan]]
            [goog.events :as events]
            [goog.net.XhrIo :as XhrIo]
            [saccade.canvashelpers :refer [clear-canvas]]
            [saccade.components.helpers :refer [instrument chkcurs]]
            [saccade.components.bitmap :refer [bitmap-width bitmap-height]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def saccader-canvas-ref "saccader-canvas")

(defn floor [num]
  (.floor js/Math num))

(defn abs [num]
  (.abs js/Math num))

(defn round-halfway-down [n interval]
  (let [magnitude (.abs js/Math n)
        direction (if (pos? n) 1 -1)
        remainder (mod magnitude interval)]
    (* direction (- magnitude (/ remainder 2)))))

(defn cell-width [{:keys [bitmap width-px]} owner]
  (/ width-px (bitmap-width bitmap)))

(defn cell-height [{:keys [bitmap height-px]} owner]
  (/ height-px (bitmap-height bitmap)))

(defn listen [el type]
  (let [port (chan)
        eventkey (events/listen el type #(put! port %1))]
    [eventkey port]))

(defn do-xhr [command-path message]
  (let [port (chan)
        path (str "http://localhost:8000" command-path)
        message (transit/write (transit/writer :json-verbose) message)]
    (println "[Client -->" path "]" message)
    (XhrIo/send path (fn [e]
                       (let [response (.. e -target getResponseText)]
                         (put! port
                               (when-not (empty? (.trim response))
                                 (println "[" path "--> Client]" response)
                                 (transit/read (transit/reader :json)
                                               response)))))
                "POST" message)
    port))

(defn paint-saccader [{:keys [world observer]} owner]
  (let [ctx (.getContext (om/get-node owner saccader-canvas-ref) "2d")
        wpcell (cell-width world owner)
        hpcell (cell-height world owner)
        xp (* wpcell (:xi observer))
        yp (* hpcell (:yi observer))
        wp (* wpcell (:width observer))
        hp (* hpcell (:height observer))]
    (clear-canvas ctx)
    (set! (.-strokeStyle ctx) "blue")
    (set! (.-lineWidth ctx) 5)
    (.strokeRect ctx xp yp wp hp)
    (when-let [dxp (om/get-state owner :dxp)]
      (let [dyp (om/get-state owner :dyp)
            snappydxp (round-halfway-down dxp wpcell)
            snappydyp (round-halfway-down dyp hpcell)]
        (set! (.-strokeStyle ctx) "rgba(0,0,255,0.25)")
        (.strokeRect ctx (+ xp snappydxp) (+ yp snappydyp) wp hp)))))

(defn sensory-data [world observer]
  (let [{:keys [bitmap]} world
        {:keys [xi width yi height]} observer]
    (vec (for [xi (range xi (+ xi width))]
           (subvec (nth bitmap xi) yi (+ yi height))))))

(defn add-action-and-result [dxi dyi world observer owner]
  (go (let [port (do-xhr "/add-action-and-result"
                         {"context_id" (:server-token @observer)
                          "motor_value" [dxi dyi]
                          "new_sensor_value" (sensory-data @world @observer)})
            response (<! port)
            log-entry {:sdr (into (sorted-set) (response "sp_output"))
                       :sensor-value (response "sensor_value")}]
        (put! (om/get-shared owner :sdr-channel) log-entry))))

(defn handle-saccader-panning [{:keys [world observer]} owner]
  (chkcurs observer)
  (let [bitmap (chkcurs (:bitmap world))]
    (go-loop []
      (let [downevt (<! (om/get-state owner :mousedown))]
        (when (= (.-button downevt) 0)
          (om/set-state! owner :grabbed true)
          (let [[kmousemove moves] (listen js/window "mousemove")
                [kmouseup ups] (listen js/window "mouseup")]
            (while (not= ups
                         (let [[evt port] (alts! [moves ups])]
                           (om/set-state! owner :dxp (- (.-clientX evt)
                                                        (.-clientX downevt)))
                           (om/set-state! owner :dyp (- (.-clientY evt)
                                                        (.-clientY downevt)))
                           port)))
            (events/unlistenByKey kmousemove)
            (events/unlistenByKey kmouseup)

            ;; In obscure cases (e.g. javascript breakpoints)
            ;; there are stale mousedowns sitting in the queue.
            (while (let [[_ port] (alts! [(om/get-state owner :mousedown)]
                                         :default :drained)]
                     (not= :default port))))

          (let [{:keys [dxp dyp]} (om/get-state owner)]
            (when (not (nil? dxp))
              (let [dxi (-> dxp
                            abs
                            (/ (cell-width @world owner))
                            floor
                            (cond-> (neg? dxp) (* -1)))
                    dyi (-> dyp
                            abs
                            (/ (cell-height @world owner))
                            floor
                            (cond-> (neg? dyp) (* -1)))
                    xi (+ (@observer :xi) dxi)
                    yi (+ (@observer :yi) dyi)]
                (when (and (<= 0 xi)
                           (<= (+ xi (@observer :width))
                               (bitmap-width @bitmap))
                           (<= 0 yi)
                           (<= (+ yi (@observer :height))
                               (bitmap-height @bitmap)))
                  (om/transact! observer :xi #(+ % dxi))
                  (om/transact! observer :yi #(+ % dyi))
                  (add-action-and-result dxi dyi world observer owner)))
              (om/set-state! owner :dxp nil)
              (om/set-state! owner :dyp nil)))
          (om/set-state! owner :grabbed false)))
      (recur))))

(def lens-component
  (instrument
   (fn lens-component [{:keys [world observer]} owner]
     (chkcurs world)
     (chkcurs observer)
     (reify
       om/IInitState
       (init-state [_]
         {:grabbed false
          :mousedown (chan)
          :dxp nil
          :dyp nil})

       om/IWillMount
       (will-mount [_]
         (handle-saccader-panning {:world world :observer observer} owner)
         (when (nil? (:server-token observer))
           (go
             (let [token (<! (do-xhr "/set-initial-sensor-value"
                                     (sensory-data @world @observer)))]
               (om/update! observer :server-token token)
               (println "Server assigned us token" token)))))

       om/IDidMount
       (did-mount [_]
         (paint-saccader {:world world :observer observer} owner))

       om/IDidUpdate
       (did-update [_ prev-props prev-state]
         (paint-saccader {:world world :observer observer} owner))

       om/IRenderState
       (render-state [_ {:keys [style grabbed mousedown]}]
         (dom/canvas #js {:ref saccader-canvas-ref
                          :width (:width-px world) :height (:height-px world)
                          :className (if grabbed "grabbed" "grab")
                          :onMouseDown (fn [e] (.persist e) (put! mousedown e))
                          :style (clj->js style)}))))))
