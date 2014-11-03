(ns saccade.components.lens
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cognitect.transit :as transit]
            [cljs.core.async :refer [<! put! alts! chan]]
            [goog.events :as events]
            [goog.net.XhrIo :as XhrIo]
            [saccade.canvashelpers :as canvas]
            [saccade.bitmaphelpers :as bitmap]
            [saccade.components.helpers :refer [instrument chkcurs]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

;; ############################################################################
;; Math help

(defn floor [num]
  (.floor js/Math num))

(defn abs [num]
  (.abs js/Math num))

;; ############################################################################
;; Exporting data from beneath the lens

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

(defn bits-under-lens [bitmap lens]
  (let [{:keys [xi yi wi hi]} lens]
    (vec (for [xi (range xi (+ xi wi))]
           (subvec (nth bitmap xi) yi (+ yi hi))))))

(defn add-action-and-result [dxi dyi bitmap lens owner]
  (go (let [port (do-xhr "/add-action-and-result"
                         {"context_id" (:server-token lens)
                          "motor_value" [dxi dyi]
                          "new_sensor_value" (bits-under-lens bitmap lens)})
            response (<! port)
            log-entry {:sdr (into (sorted-set) (response "sp_output"))
                       :sensor-value (response "sensor_value")}]
        (put! (om/get-shared owner :sdr-channel) log-entry))))

;; ############################################################################
;; Saccading the lens

(defn commit-change [bitmap wpbitmap hpbitmap lens owner]
  (let [{:keys [dxp dyp]} (om/get-state owner)]
    (when (not (nil? dxp))
      (let [{:keys [wi hi wpcell hpcell]} (bitmap/onto-px bitmap
                                                          wpbitmap hpbitmap)
            dxi (-> dxp
                    abs
                    (/ wpcell)
                    floor
                    (cond-> (neg? dxp) (* -1)))
            dyi (-> dyp
                    abs
                    (/ hpcell)
                    floor
                    (cond-> (neg? dyp) (* -1)))
            xi (+ (@lens :xi) dxi)
            yi (+ (@lens :yi) dyi)]
        (when (and (<= 0 xi)
                   (<= (+ xi (@lens :wi)) wi)
                   (<= 0 yi)
                   (<= (+ yi (@lens :hi)) hi))
          (om/transact! lens :xi #(+ % dxi))
          (om/transact! lens :yi #(+ % dyi))
          (add-action-and-result dxi dyi bitmap @lens owner)))
      (om/set-state! owner :dxp nil)
      (om/set-state! owner :dyp nil))))

;; ############################################################################
;; Drag-drop

(defn listen [el type]
  (let [port (chan)
        eventkey (events/listen el type #(put! port %1))]
    [eventkey port]))

(defn round-halfway-down [n interval]
  (let [magnitude (.abs js/Math n)
        direction (if (pos? n) 1 -1)
        remainder (mod magnitude interval)]
    (* direction (- magnitude (/ remainder 2)))))

(defn handle-panning [owner on-commit]
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
          (events/unlistenByKey kmouseup))

        ;; In obscure cases (e.g. javascript breakpoints)
        ;; there are stale mousedowns sitting in the queue.
        (while (let [[_ port] (alts! [(om/get-state owner :mousedown)]
                                     :default :drained)]
                 (not= :default port)))

        (on-commit)

        (om/set-state! owner :grabbed false)))
    (recur)))

;; ############################################################################
;; Display logic

(def lens-ref "lens-canvas")

(defn paint [bitmap wpbitmap hpbitmap lens owner]
  (let [ctx (.getContext (om/get-node owner lens-ref) "2d")
        {:keys [wpcell hpcell]} (bitmap/onto-px bitmap wpbitmap hpbitmap)
        xp (* wpcell (:xi lens))
        yp (* hpcell (:yi lens))
        wp (* wpcell (:wi lens))
        hp (* hpcell (:hi lens))]
    (canvas/clear ctx)
    (set! (.-strokeStyle ctx) "blue")
    (set! (.-lineWidth ctx) 5)
    (.strokeRect ctx xp yp wp hp)
    (when-let [dxp (om/get-state owner :dxp)]
      (let [dyp (om/get-state owner :dyp)
            snappydxp (round-halfway-down dxp wpcell)
            snappydyp (round-halfway-down dyp hpcell)]
        (set! (.-strokeStyle ctx) "rgba(0,0,255,0.25)")
        (.strokeRect ctx (+ xp snappydxp) (+ yp snappydyp) wp hp)))))

(def lens-component
  (instrument
   (fn lens-component [{:keys [bitmap lens view-config]} owner]
     (reify
       om/IInitState
       (init-state [_]
         {:grabbed false
          :mousedown (chan)
          :dxp nil
          :dyp nil})

       om/IWillMount
       (will-mount [_]
         (handle-panning owner
                         (fn []
                           (commit-change @bitmap
                                          (:wp @view-config)
                                          (:hp @view-config)
                                          lens
                                          owner)))
         (when (nil? (:server-token lens))
           (go
             (let [token (<! (do-xhr "/set-initial-sensor-value"
                                     (bits-under-lens @bitmap @lens)))]
               (om/update! lens :server-token token)
               (println "Server assigned us token" token)))))

       om/IDidMount
       (did-mount [_]
         (paint bitmap (:wp view-config) (:hp view-config) lens owner))

       om/IDidUpdate
       (did-update [_ prev-props prev-state]
         (paint bitmap (:wp view-config) (:hp view-config) lens owner))

       om/IRenderState
       (render-state [_ {:keys [style grabbed mousedown]}]
         (dom/canvas #js {:ref lens-ref
                          :width (:wp view-config) :height (:hp view-config)
                          :className (if grabbed "grabbed" "grab")
                          :onMouseDown (fn [e] (.persist e) (put! mousedown e))
                          :style (clj->js style)}))))))
