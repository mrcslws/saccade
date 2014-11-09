(ns saccade.htm.cloud
  (:require [om.core :as om :include-macros true]
            [cognitect.transit :as transit]
            [cljs.core.async :refer [<! put! chan mult tap alts!]]
            [goog.net.XhrIo :as XhrIo])
  (:require-macros [saccade.macros :refer [go-monitor go-result drain!]]
                   [cljs.core.async.macros :refer [go go-loop alt!]]))


(defn ^:private do-xhr [command-path message]
  (let [port (chan)
        path (str "http://localhost:8000" command-path)
        message (transit/write (transit/writer :json-verbose) message)]
    (println "[Client -->" path "]" message)
    (XhrIo/send path (fn [e]
                       (let [status (.. e -target getStatus)
                             response (.. e -target getResponseText)]
                         (println "[" path "--> Client]")
                         (put! port
                               (if (= status 200)
                                 (do
                                   (println response)
                                   (transit/read (transit/reader :json)
                                                 response))
                                 (do
                                   (js/console.error (str "XHR failed: "
                                                          status))
                                   :failure)))))
                "POST" message)
    port))

(def ^:private impls
  {:initialize (fn [htm-bridge _ current-sensor-value]
                 (go-result
                  (cond

                   ;; Only reinitialize if we haven't initialized yet,
                   ;; or if the lens is not where we think it should
                   ;; be.
                   (and (:server-token @htm-bridge)
                        (= current-sensor-value (:sensor-value @htm-bridge)))
                   :success

                   (let [response (<! (do-xhr "/set-initial-sensor-value"
                                              current-sensor-value))]
                     (when-not (= response :failure)
                       (om/update! htm-bridge :server-token
                                   response)
                       (om/update! htm-bridge :sensor-value
                                   current-sensor-value)
                       true))
                   :success

                   :else
                   :failure)))
   :saccade (fn [htm-bridge {:keys [sdrs]} motor-value new-sensor-value]
              (go-result
               (cond
                (let [message {"context_id" (:server-token @htm-bridge)
                               "motor_value" motor-value
                               "new_sensor_value" new-sensor-value}
                      response (<! (do-xhr "/add-action-and-result" message))]
                  (when (not= response :failure)
                    (om/update! htm-bridge :sensor-value new-sensor-value)
                    (put! sdrs {:sdr (into (sorted-set) (response "sp_output"))
                                :sensor-value (response "sensor_value")})
                    true))
                :success

                :else
                :failure)))})

(defn cloud-htm-bridge [htm-bridge donemult]
  (let [commands (chan)
        sdrs (chan)]
    (go-monitor commands donemult
                [[command checkpoint & args]]
                (when-not
                    (= :success
                       (if-let [impl (get impls command)]
                         (<! (apply impl htm-bridge {:sdrs sdrs} args))
                         (js/console.error (str "Unrecognized command: "
                                                command))))

                  ;; Failed.
                  (when-let [[cursor value] checkpoint]
                    (om/update! cursor value))
                  (drain! commands)))
    [commands sdrs]))
