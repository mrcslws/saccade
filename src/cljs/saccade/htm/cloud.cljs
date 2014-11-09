(ns saccade.htm.cloud
  (:require [om.core :as om :include-macros true]
            [cognitect.transit :as transit]
            [cljs.core.async :refer [<! put! chan mult tap alts!]]
            [goog.net.XhrIo :as XhrIo])
  (:require-macros [saccade.macros :refer [go-monitor drain!]]
                   [cljs.core.async.macros :refer [go go-loop alt!]]))


(defn ^:private do-xhr [command-path message]
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

(def ^:private impls
  {:initialize (fn [htm-bridge _ current-sensor-value]
                 (let [result (chan)]
                   (go
                     ;; Only reinitialize if we haven't initialized yet,
                     ;; or if the lens is not where we think it should
                     ;; be.
                     (when (or (nil? (:server-token @htm-bridge))
                               (not= current-sensor-value
                                     (:sensor-value @htm-bridge)))
                       (om/update! htm-bridge :server-token
                                   (<!
                                    (do-xhr "/set-initial-sensor-value"
                                            current-sensor-value)))
                       (om/update! htm-bridge :sensor-value
                                   current-sensor-value))
                     (put! result :success))
                   result))
   :saccade (fn [htm-bridge {:keys [sdrs]} motor-value new-sensor-value]
              (let [result (chan)]
                (go
                  (let [response (<! (do-xhr
                                      "/add-action-and-result"
                                      {"context_id" (:server-token @htm-bridge)
                                       "motor_value" motor-value
                                       "new_sensor_value" new-sensor-value}))]
                    (om/update! htm-bridge :sensor-value new-sensor-value)
                    (put! sdrs
                          {:sdr (into (sorted-set) (response "sp_output"))
                           :sensor-value (response "sensor_value")})
                    (put! result :success)))
                result))})

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
