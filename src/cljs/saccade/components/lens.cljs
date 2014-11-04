(ns saccade.components.lens
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cognitect.transit :as transit]
            [cljs.core.async :refer [<! put! alts! chan mult tap close!]]
            [goog.net.XhrIo :as XhrIo]
            [saccade.drag :as drag]
            [saccade.components.helpers :refer [log-lifecycle-mixin]]
            [saccade.canvashelpers :as canvas]
            [saccade.bitmaphelpers :as bitmap])
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]]))

;; ############################################################################
;; Math help

(defn floor [num]
  (.floor js/Math num))

(defn abs [num]
  (.abs js/Math num))

(defn round-toward-zero [d]
  (-> d
      abs
      floor
      (cond-> (neg? d) (* -1))))

(defn dp->di [dxp dyp {:keys [wpcell hpcell]}]
  [(-> dxp
       (/ wpcell)
       round-toward-zero)
   (-> dyp
       (/ hpcell)
       round-toward-zero)])

(defn in-bounds? [{:keys [xi yi wi hi]} bitmap]
  (and (<= 0 xi)
       (<= (+ xi wi) (bitmap/width bitmap))
       (<= 0 yi)
       (<= (+ yi hi) (bitmap/height bitmap))))

(defn round-down-to-nearest [d interval]
  (-> d
      (/ interval)
      round-toward-zero
      (* interval)))

(defn round-down-halfway-to-nearest [d interval]
  (-> d
      (+ (round-down-to-nearest d interval))
      (/ 2)))

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
  (let [{:keys [xi yi wi hi]} lens
        bitmap (if (om/cursor? bitmap) (om/value bitmap) bitmap)]
    (vec (for [xi (range xi (+ xi wi))]
           (subvec (nth bitmap xi) yi (+ yi hi))))))

(defn lens-server-conversation [bitmap lens moves sdrs donemult]
  (go
    (when (nil? (:server-token @lens))
      (om/update! lens :server-token
                    (<! (do-xhr "/set-initial-sensor-value"
                              (bits-under-lens @bitmap @lens)))))
    (let [done (chan)]
      (tap donemult done)
      (go-loop []
        (alt!
          moves
          ([[dxi dyi]]
             (let [xi (+ (:xi @lens) dxi)
                   yi (+ (:yi @lens) dyi)]
               (when (in-bounds? @lens @bitmap)
                 (om/transact! lens #(assoc % :xi xi :yi yi))
                 (let [sensed (bits-under-lens @bitmap @lens)
                       response (<! (do-xhr "/add-action-and-result"
                                            {"context_id" (:server-token @lens)
                                             "motor_value" [dxi dyi]
                                             "new_sensor_value" sensed}))]
                   (put! sdrs {:sdr (into (sorted-set) (response "sp_output"))
                               :sensor-value (response "sensor_value")})))
               (recur)))

          done
          :goodbye)))))


;; ############################################################################
;; Display and interaction logic

(def lens-ref "lens-canvas")

(defn paint [bitmap lens view owner]
  (let [ctx (.getContext (om/get-node owner lens-ref) "2d")
        {:keys [wpcell hpcell]} (bitmap/onto-px bitmap view)
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
            snappydxp (round-down-halfway-to-nearest dxp wpcell)
            snappydyp (round-down-halfway-to-nearest dyp hpcell)]
        (set! (.-strokeStyle ctx) "rgba(0,0,255,0.25)")
        (.strokeRect ctx (+ xp snappydxp) (+ yp snappydyp) wp hp)))))

(defn monitor [chn donemult handler]
  (let [done (chan)]
    (tap donemult done)
    (go-loop []
      (alt!
        chn
        ([v]
           (handler v)
           (recur))

        done
        :goodbye))))

(defcomponent lens-component [{:keys [bitmap lens view]} owner]
  (:mixins log-lifecycle-mixin)
  (init-state
   [_]
   (let [to-mult (chan)]
     {:grabbed false
      :mousedown (chan)
      :teardown-in to-mult
      :teardown (mult to-mult)
      :dxp nil
      :dyp nil}))

  (will-mount
   [_]
   (let [started (chan)
         progress (chan)
         finished (chan)
         commits (chan)
         {:keys [mousedown teardown]} (om/get-state owner)]
     (drag/watch mousedown started progress finished)

     (monitor started teardown
              #(om/set-state! owner :grabbed true))
     (monitor progress teardown
              (fn [[dxp dyp]]
                (om/update-state! owner #(assoc % :dxp dxp :dyp dyp))))
     (monitor finished teardown
              (fn [[dxp dyp]]
                (let [view+ (bitmap/onto-px @bitmap @view)
                      [dxi dyi] (dp->di dxp dyp view+)]
                  (om/update-state! owner #(assoc %
                                             :dxp nil :dyp nil
                                             :grabbed false))

                  (put! commits [dxi dyi]))))

     (lens-server-conversation bitmap lens commits
                               (om/get-shared owner :sdr-channel)
                               teardown)))

  (will-unmount
   [_]
   (let [{:keys [teardown-in mousedown]} (om/get-state owner)]
     (put! teardown-in :destroy-everything)
     (close! mousedown)))

  (did-mount
   [_]
   (paint bitmap lens view owner))

  (did-update
   [_ prev-props prev-state]
   (paint bitmap lens view owner))

  (render-state
   [_ {:keys [style grabbed mousedown]}]
   (dom/canvas #js {:ref lens-ref
                    :width (:wp view) :height (:hp view)
                    :className (if grabbed "grabbed" "grab")
                    :onMouseDown (fn [e] (.persist e) (put! mousedown e))
                    :style (clj->js style)})))
