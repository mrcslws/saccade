(ns saccade.components.sdrjournal
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as string]
            [cljs.core.async :refer [<!]]
            [saccade.components.helpers :refer [instrument]]
            [saccade.components.bitmap :refer [bitmap-component]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defn log-entry [[sensor-value sdr-log]]
  (apply dom/div nil
         (om/build bitmap-component {:bitmap sensor-value
                                      :view-config {:wp 100 :hp 100}})

         (map (fn [{:keys [sdr count]}]
                (dom/div nil
                         (dom/div #js {:style
                                       #js {:width 100
                                            :font-family "Consolas"}}
                                  (string/join " " sdr))
                         (dom/div nil count)))
              sdr-log)))

(def show-logs? true)
(def sdrjournal-component
  (instrument
   (fn sdrjournal-component [{:keys [sdr-journal]} owner]
     (reify
       om/IWillMount
       (will-mount [_]
         ;; TODO - close this go-loop on unmount
         (go-loop []
           (let [{:keys [sdr sensor-value]}
                 (<! (om/get-shared owner :sdr-channel))]
             ;; Structure:
             ;; {sensor-value1 [{:sdr sdr1 :count count1}
             ;;                 {:sdr sdr2 :count count2}]}
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
         (when show-logs? (apply dom/div nil
                                 (map log-entry sdr-journal))))))))
