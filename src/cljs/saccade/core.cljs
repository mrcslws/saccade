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

;; Naming conventions:
;; x and y use the upper-left corner as the origin
;; [xi yi wi hi]
;; the x-index, y-index, width, and height, in terms of the grid
;; [xp yp wp hp]
;; x, y, width, and height in terms of pixels
;; [d]
;; Delta. dxi, dxp, etc.
;; [vf]
;; The "visual field"

(def app-state
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
               :server-token nil}}))

(defn bitmap-width [bitmap]
  (count bitmap))

(defn bitmap-height [bitmap]
  (count (first bitmap)))

;; ============================================================================
;; Component: world-view

(defn clear-canvas [ctx]
  (.save ctx)
  (.setTransform ctx 1 0 0 1 0 0)
  (.clearRect ctx 0 0 (.. ctx -canvas -width) (.. ctx -canvas -height))
  (.restore ctx))

(def canvas-ref "world-canvas")

(defn paint-world [{:keys [bitmap width-px height-px]} owner]
  (let [ctx (.getContext (om/get-node owner canvas-ref) "2d")
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

(defn world-view [{:keys [width-px height-px] :as world} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (paint-world world owner))

    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (paint-world world owner))

    om/IRenderState
    (render-state [_ {:keys [style]}]
      (dom/canvas #js {:ref canvas-ref :width width-px :height height-px
                       :style (clj->js style)}))))


;; ============================================================================
;; Component: saccader-view

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
  (let [ctx (.getContext (om/get-node owner canvas-ref) "2d")
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

(defn handle-saccader-panning [app owner]
  (let [{:keys [world observer]} app
        {:keys [bitmap]} world
        {obs-width :width obs-height :height} observer]
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

(def canvas-ref "saccader-canvas")
(defn saccader-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:grabbed false
       :mousedown (chan)
       :dxp nil
       :dyp nil})

    om/IWillMount
    (will-mount [_]
      (handle-saccader-panning app owner)
      (go
        (let [token (<! (do-xhr "/set-initial-sensor-value"
                                (sensory-data (:world @app)
                                              (:observer @app))))]
          (om/update! app [:observer :server-token] token)
          (println "Server assigned us token" token))))

    om/IDidMount
    (did-mount [_]
      (paint-saccader app owner))

    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (paint-saccader app owner))

    om/IRenderState
    (render-state [_ {:keys [style grabbed mousedown]}]
      (let [world (:world app)]
        (dom/canvas #js {:ref canvas-ref
                         :width (:width-px world) :height (:height-px world)
                         :className (if grabbed "grabbed" "grab")
                         :onMouseDown (fn [e] (.persist e) (put! mousedown e))
                         :style (clj->js style)
                         })))))

;; ============================================================================
;; Component: world-and-observer-view

(defn world-and-observer-view [app owner]
  (let [{:keys [world]} app
        {:keys [width-px height-px]} world]
    (reify
      om/IRenderState
      (render-state [_ {:keys [logchan]}]
        (dom/div #js {:position "relative"
                      :style #js {:width width-px :height height-px}}
                 (om/build world-view world
                           {:init-state
                            {:style {:position "absolute"
                                     :left 0 :top 0 :zIndex 0}}})
                 (om/build saccader-view app
                           {:init-state
                            {:logchan logchan
                             :style {:position "absolute"
                                     :left 0 :top 0 :zIndex 1}}}))))))

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

;; TODO - as I write this, every time I save it destroys the log.
;; When I find myself being annoyed by this kind of thing, I bet the right
;; answer is that I should store it in the app-state (and defonce the app state)
(defn log-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {;; Structure:
       ;; {sensor-value1 [{:sdr sdr1 :count count1}
       ;;                 {:sdr sdr2 :count count2}]}
       :sdr-journal {}})

    om/IWillMount
    (will-mount [_]
      (go-loop []
        (let [{:keys [sdr sensor-value]} (<! (om/get-state owner :logchan))]
          (om/update-state! owner [:sdr-journal sensor-value]
                            (fn [sdr-log]
                              (if (not= sdr (:sdr (last sdr-log)))
                                (vec (conj sdr-log {:sdr sdr :count 1}))
                                (update-in sdr-log [(dec (count sdr-log))
                                                    :count]
                                           inc)))))
        (recur)))

    om/IRenderState
    (render-state [_ {:keys [sdr-journal]}]
      (apply dom/div nil
             (map log-entry sdr-journal)))))

;; ============================================================================

(defn main []
  (let [logchan (chan)]
    (om/root world-and-observer-view app-state
             {:target (.getElementById js/document "app")
              :init-state {:width 500 :height 500 :logchan logchan}})
    (om/root log-view app-state
             {:target (.getElementById js/document "log")
              :init-state {:logchan logchan}})))
