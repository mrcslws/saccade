(ns saccade.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cognitect.transit :as transit]
            [clojure.string :as string]
            [cljs.core.async :refer [<! put! alts! chan]]
            [goog.events :as events]
            [goog.net.XhrIo :as XhrIo])
  (:require-macros [saccade.macros :refer [set-prefixed!]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

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

(defonce app-state
  (atom
   {:world {:bitmap [[0 0 0 0 0 0 0 0 0]
                     [0 0 0 0 0 0 0 0 0]
                     [0 0 0 0 0 0 0 0 0]
                     [0 0 0 0 0 0 0 0 0]
                     [0 0 0 0 1 0 0 0 0]
                     [0 0 0 0 0 1 0 0 0]
                     [0 0 0 0 0 0 0 0 0]
                     [0 0 0 0 0 0 0 0 0]
                     [0 0 0 0 0 0 0 0 0]]
            :width-px 500
            :height-px 500}
    :observer {:xi 3 :yi 3 :width 3 :height 3
               :server-token nil}
    ;; Structure:
    ;; {sensor-value1 [{:sdr sdr1 :count count1}
    ;;                 {:sdr sdr2 :count count2}]}
    :sdr-journal {}}))

(defn bitmap-width [bitmap]
  (count bitmap))

(defn bitmap-height [bitmap]
  (count (first bitmap)))

;; ============================================================================
;; Component: world-view

(def world-canvas-ref "world-canvas")

(defn clear-canvas [ctx]
  (.save ctx)
  (.setTransform ctx 1 0 0 1 0 0)
  (.clearRect ctx 0 0 (.. ctx -canvas -width) (.. ctx -canvas -height))
  (.restore ctx))

(defn paint-world [{:keys [bitmap width-px height-px]} owner]
  (let [ctx (.getContext (om/get-node owner world-canvas-ref) "2d")
        wi (bitmap-width bitmap)
        hi (bitmap-height bitmap)
        wpcell (/ width-px wi)
        hpcell (/ height-px hi)]
    (clear-canvas ctx)
    (set! (.-fillStyle ctx) "black")
    (set! (.-strokeStyle ctx) "#2E7DD1")
    (set! (.-lineWidth ctx) 1)
    (doseq [xi (range wi)
            yi (range hi)
            :let [xp (* xi wpcell)
                  yp (* yi hpcell)]]
      ;; Paint the dot, if applicable.
      (when (= 1 (-> bitmap (nth xi) (nth yi)))
        (.fillRect ctx xp yp wpcell hpcell))
      ;; Paint the grid.
      (.strokeRect ctx xp yp wpcell hpcell))))

(def world-view
  (instrument
   (fn world-view [world owner]
     (reify
       om/IDidMount
       (did-mount [_]
         (paint-world world owner))

       om/IDidUpdate
       (did-update [_ prev-props prev-state]
         (paint-world world owner))

       om/IRenderState
       (render-state [_ {:keys [style]}]
         (dom/canvas #js {:ref world-canvas-ref :width (:width-px world)
                          :height (:height-px world) :style (clj->js style)}))))))

;; ============================================================================
;; Component: saccader-view

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
        (put! (om/get-state owner :logchan) log-entry))))

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

(def saccader-view
  (instrument
   (fn saccader-view [{:keys [world observer]} owner]
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
                          :style (clj->js style)
                          }))))))

;; ============================================================================
;; Component: world-and-observer-view
(def world-and-observer-view
  (instrument
   (fn world-and-observer-view [app owner]
     (let [world (chkcurs (:world app))
           observer (chkcurs (:observer app))]
       (reify
         om/IRenderState
         (render-state [_ {:keys [logchan]}]
           (dom/div #js {:position "relative"
                         :style #js {:width (:width-px world)
                                     :height (:height-px world)}}
                    (om/build world-view world
                              {:init-state
                               {:style {:position "absolute"
                                        :left 0 :top 0 :zIndex 0}}})
                    (om/build saccader-view {:world world :observer observer}
                              {:init-state
                               {:logchan logchan
                                :style {:position "absolute"
                                        :left 0 :top 0 :zIndex 1}}}))))))))

;; ============================================================================
;; Component: log-view

(defn log-entry [[sensor-value sdr-log]]
  (apply dom/div nil
         (om/build world-view {:bitmap sensor-value
                                      :width-px 100 :height-px 100})

         (map (fn [{:keys [sdr count]}]
                (dom/div nil
                         (dom/div #js {:style
                                       #js {:width 100
                                            :font-family "Consolas"}}
                                  (string/join " " sdr))
                         (dom/div nil count)))
              sdr-log)))

(def log-view
  (instrument
   (fn log-view [{:keys [sdr-journal]} owner]
     (reify
       om/IWillMount
       (will-mount [_]
         ;; TODO - close this go-loop on unmount

         (go-loop []
           (let [{:keys [sdr sensor-value]} (<! (om/get-state owner :logchan))]
             ()
             (om/transact! sdr-journal [sensor-value]
                           (fn [sdr-log]
                             (if (not= sdr (:sdr (last sdr-log)))
                               (vec (conj sdr-log {:sdr sdr :count 1}))
                               (update-in sdr-log [(dec (count sdr-log))
                                                   :count]
                                          inc)))))
           (recur)))

       om/IRender
       (render [_]
         (apply dom/div nil
                (map log-entry sdr-journal)))))))

;; ============================================================================


(defn main []
  (let [logchan (chan)]
    (om/root world-and-observer-view app-state
             {:target (.getElementById js/document "app")
              :init-state {:width 500 :height 500 :logchan logchan}})
    (om/root log-view app-state
             {:target (.getElementById js/document "log")
              :init-state {:logchan logchan}})))
