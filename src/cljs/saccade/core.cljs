(ns saccade.core
  (:require [clojure.browser.repl]
            [cognitect.transit :as transit]
            [saccade.dom :refer [create-styled-dom]]
            [clojure.string :as string]
            [cljs.core.async :refer [<! put! alts! chan]]
            [goog.dom :as dom]
            [goog.events :as events])
  (:require-macros [saccade.macros :refer [set-prefixed!]]
                   [cljs.core.async.macros :refer [go]]))

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

(defn log [& args]
  (.log js/console (reduce str "" args)))

(def worldwp 500)
(def worldhp 500)
(def worldwi 9)
(def worldhi 9)
(def vfxi nil)
(def vfyi nil)
(def vfwi 3)
(def vfhi 3)

(defn floor [num]
  (.floor js/Math num))

(defn xi->xp [xi]
  (* (/ xi worldwi) worldwp))

(defn yi->yp [yi]
  (* (/ yi worldhi) worldhp))

(defn xp->xifraction [xp]
  (* (/ xp worldwp) worldwi))

(defn yp->yifraction [yp]
  (* (/ yp worldhp) worldhi))

(defn xp->xi [xp]
  (floor (xp->xifraction xp)))

(defn yp->yi [yp]
  (floor (yp->yifraction yp)))

(defn dxp->dxi [dxp]
  (let [magnitude (.abs js/Math dxp)
        direction (if (pos? dxp) 1 -1)]
    (* direction (xp->xi magnitude))))

(defn dyp->dyi [dyp]
  (let [magnitude (.abs js/Math dyp)
        direction (if (pos? dyp) 1 -1)]
    (* direction (yp->yi magnitude))))

(def the-world nil)
(set! the-world [[0 0 0 0 0 0 0 0 0]
                 [0 0 0 0 0 0 0 0 0]
                 [0 0 0 0 0 0 0 0 0]
                 [0 0 0 0 0 0 0 0 0]
                 [0 0 0 0 1 0 0 0 0]
                 [0 0 0 0 0 1 0 0 0]
                 [0 0 0 0 0 0 0 0 0]
                 [0 0 0 0 0 0 0 0 0]
                 [0 0 0 0 0 0 0 0 0]])

(def canvas (dom/createDom "canvas"
                           (clj->js {"width" worldwp
                                     "height" worldhp})))
(def ctx (.getContext canvas "2d"))

(defn paint-vf-rectangle [style xp yp]
  (set! (.-strokeStyle ctx) style)
  (set! (.-lineWidth ctx) 5)
  (.strokeRect ctx xp yp
               (xi->xp vfwi) (yi->yp vfhi)))

(defn paint-vf []
  (paint-vf-rectangle "blue" (xi->xp vfxi) (yi->yp vfyi)))

(defn paint-grid [wi hi wp hp dots ctx]
  (set! (.-fillStyle ctx) "black")
  (set! (.-strokeStyle ctx) "#2E7DD1")
  (set! (.-lineWidth ctx) 1)
  (let [wpcell (/ wp wi)
        hpcell (/ hp hi)]
    (doseq [xi (range wi)
            yi (range hi)
            :let [xp (* xi wpcell)
                  yp (* yi hpcell)]]
      ;; Paint the dot, if applicable.
      (when (= 1 (-> dots (nth xi) (nth yi)))
        (.fillRect ctx xp yp wpcell hpcell))
      ;; Paint the grid.
      (.strokeRect ctx xp yp wpcell hpcell))))

(defn clear-canvas []
  (.save ctx)
  (.setTransform ctx 1 0 0 1 0 0)
  (.clearRect ctx 0 0 worldwp worldhp)
  (.restore ctx))

(defn paint []
  (clear-canvas)
  (paint-grid worldwi worldhi worldwp worldhp the-world ctx)
  (paint-vf))

(defn sensory-data []
  (vec (for [xi (range vfxi (+ vfxi vfwi))]
         (subvec (nth the-world xi) vfyi (+ vfyi vfhi)))))

(defn do-xhr [command-path message]
  (let [xhr (js/XMLHttpRequest.)
        w (transit/writer :json-verbose)
        r (transit/reader :json)
        path (str "http://localhost:8000" command-path)
        message (transit/write w message)]
    (log "[Client --> " path "] " message)
    (.open xhr "post" path false)
    (.send xhr message)
    (let [response (.-responseText xhr)]
      (when (not (empty? (.trim response)))
        (log "[" path " --> Client] " response)
        (transit/read r response)))))

(def server-token nil)

(defn set-initial-sensor-value []
  (set! server-token (do-xhr "/set-initial-sensor-value" (sensory-data)))
  (log "Server assigned us token " server-token))

(defn create-snapshot-element []
  (let [sscnvs (.createElement js/document "canvas")
        sswidth 100
        ssheight 100]
    (doseq [[k v] {"width" sswidth
                   "height" ssheight}]
      (.setAttribute sscnvs k v))
    (paint-grid 3 3 sswidth ssheight (sensory-data) (.getContext sscnvs "2d"))
    sscnvs))

;; Structure:
;; { sensor_value {:sdrs [{:sdr sdr1
;;                         :statistics {:count count1
;;                                      :count-element el}}
;;                        {:sdr sdr2
;;                         :statistics {:count count1
;;                                      :count-element el}}]
;;                 :container-element el
;;                }}
(def sensor-sdrs {})

(defn add-action-and-result [dxi dyi]
  (let [response (do-xhr "/add-action-and-result"
                         {"context_id" server-token
                          "motor_value" [dxi dyi]
                          "new_sensor_value" (sensory-data)})
        sp-columns (into (sorted-set) (response "sp_output"))
        data (response "sensor_value")]
    (when (not (contains? sensor-sdrs data))
      (let [sdrlog-el (dom/createElement "div")]
        (dom/append js/document.body
                    (dom/createDom "div" nil
                                   (create-snapshot-element)
                                   sdrlog-el))
        (set! sensor-sdrs
              (assoc-in sensor-sdrs [data]
                        {:sdrs []
                         :container-element sdrlog-el}))))
    (when (not (= sp-columns (-> sensor-sdrs (get-in [data :sdrs]) last :sdr)))
      (let [count-el (dom/createDom "div" nil 0)]
        (dom/append (get-in sensor-sdrs [data :container-element])
                    (create-styled-dom "div" {"width" "100px"
                                              "font-family" "Consolas"}
                                       nil (string/join " " sp-columns))
                    count-el)
        (set! sensor-sdrs
              (update-in sensor-sdrs [data :sdrs]
                         conj {:sdr sp-columns
                               :statistics {:count 0
                                            :count-element count-el}}))))
    (let [idx (-> sensor-sdrs (get-in [data :sdrs]) count dec)]
      (set! sensor-sdrs
            (update-in sensor-sdrs [data :sdrs idx :statistics :count] inc))
      (let [statistics (get-in sensor-sdrs [data :sdrs idx :statistics])]
        (set! (.-innerHTML (:count-element statistics))
              (:count statistics))))))

(defn commit-vf [xi yi]
  (when (and (<= 0 xi) (< xi worldwi) (<= 0 yi) (< yi worldhi))
    (let [dxi (- xi vfxi)
          dyi (- yi vfyi)]
      (set! vfxi xi)
      (set! vfyi yi)
      (if (nil? server-token)
        (set-initial-sensor-value)
        (add-action-and-result dxi dyi))))
  (paint))

(defn paint-dream-vf [xp yp]
  (paint-vf-rectangle "rgba(0,0,255,0.25)" xp yp))

(defn consider-vf [xi yi]
  ;; TODO: During a drag, cache the values for each spot on the grid as it's
  ;; retrieved.
  )

(defn round-halfway-down [n interval]
  (let [magnitude (.abs js/Math n)
        direction (if (pos? n) 1 -1)
        remainder (mod magnitude interval)]
    (* direction (- magnitude (/ remainder 2)))))

(defn paint-partialdrag [dxp dyp]
  (let [snappydxp (round-halfway-down dxp (xi->xp 1))
        snappydyp (round-halfway-down dyp (yi->yp 1))]
    (paint)
    (paint-dream-vf (+ (xi->xp vfxi) snappydxp)
                    (+ (yi->yp vfyi) snappydyp))))

(defn listen [el type]
  (let [port (chan)
        eventkey (events/listen el type #(put! port %1))]
    [eventkey port]))

(defn handle-canvas-panning []
  (go (while true
        (let [[kmousedown downs] (listen canvas "mousedown")]
          (set-prefixed! (.-cursor (.-style canvas)) "grab")
          (let [downevt (<! downs)]
            (when (= (.-button downevt) events/BrowserEvent.MouseButton.LEFT)
              (let [[kmousemove moves] (listen js/window "mousemove")
                    [kmouseup ups] (listen js/window "mouseup")]
                (set-prefixed! (.-cursor (.-style canvas)) "grabbing")
                (while (not (= ups
                               (let [[evt port] (alts! [moves ups])
                                     dxp (- (.-clientX evt) (.-clientX downevt))
                                     dyp (- (.-clientY evt) (.-clientY downevt))
                                     newxi (+ vfxi (dxp->dxi dxp))
                                     newyi (+ vfyi (dyp->dyi dyp))]
                                 (cond
                                  (= port moves)
                                  (do
                                    (consider-vf newxi newyi)
                                    (paint-partialdrag dxp dyp))

                                  (= port ups)
                                  (commit-vf newxi newyi))
                                 port))))
                (events/unlistenByKey kmousemove)
                (events/unlistenByKey kmouseup)

                ;; In obscure cases (e.g. javascript breakpoints)
                ;; there are stale mousedowns sitting in the queue,
                ;; not paired with mouseups. Just start fresh after
                ;; every mousedown.
                (events/unlistenByKey kmousedown))))))))

(defn add-everything-to-document []
  (dom/appendChild js/document.body canvas)
  (handle-canvas-panning)
  (commit-vf 3 3))

(set! (.-onload js/window)
      (fn []
        (add-everything-to-document)))
