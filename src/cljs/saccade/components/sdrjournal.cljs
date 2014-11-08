(ns saccade.components.sdrjournal
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [clojure.string :as string]
            [cljs.core.async :refer [put! mult tap chan]]
            [saccade.components.helpers :refer [log-lifecycle]]
            [saccade.components.bitmap :refer [->bitmap-component]])
  (:require-macros [cljs.core.async.macros :refer [go-loop alt!]]))

(defcomponent logentry [[sensor-value sdr-log] owner]
  (:mixins log-lifecycle)
  (render
   [_]
   (apply dom/div #js {:style #js {:float "left" :margin-right "12"}}
          (->bitmap-component {:bitmap sensor-value
                               :view {:wp 100 :hp 100}})

          (map (fn [{:keys [sdr count]}]
                 (dom/div nil
                          (dom/code #js {:style
                                         #js {:display "block"
                                              :width 85
                                              :text-align "center"
                                              :border-right "1px dotted black"}}
                                    (string/join " " sdr))
                          (dom/div #js {:style
                                        #js {:position "relative"
                                             :top -16
                                             :left 90}}
                                   (str count))))
               (reverse sdr-log)))))

(def show-logs? true)
(defcomponent sdrjournal-component [{:keys [sdr-journal]} owner]
  (:mixins log-lifecycle)
  (init-state
   [_]
   (let [to-mult (chan)]
     {:teardown-in to-mult
      :teardown (mult to-mult)}))

  (will-mount
   [_]
   (let [teardown (om/get-state owner :teardown)
         done (chan)
         sdrs (om/get-state owner :sdr-channel)]
     (tap teardown done)
     (go-loop []
       (alt!
         sdrs
         ([{:keys [sdr sensor-value]}]
            ;; Structure:
            ;; {sensor-value1 [{:sdr sdr1 :count count1}
            ;;                 {:sdr sdr2 :count count2}]}
            (om/transact! sdr-journal [sensor-value]
                          (fn [sdr-log]
                            (if (not= sdr (:sdr (last sdr-log)))
                              (vec (conj sdr-log {:sdr sdr :count 1}))
                              (update-in sdr-log [(dec (count sdr-log)):count]
                                         inc))))
            (recur))

         done
         :goodbye))))

  (will-unmount
   [_]
   (put! (om/get-state owner :teardown-in) :destroy-everything))

  (render
   [_]
   (when show-logs?
     (apply dom/div nil
            (map ->logentry sdr-journal)))))
