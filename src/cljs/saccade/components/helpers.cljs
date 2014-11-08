(ns saccade.components.helpers
  (:require [om.core :as om :include-macros true]
            [om-tools.mixin :refer-macros [defmixin]]))

(def log-level 0)

(defn log [owner level & args]
  (when (<= level log-level)
    (apply println (.getDisplayName owner) args)))

(defmixin log-lifecycle
  (will-mount
   [owner]
   (log owner 3 "will-mount"))
  (did-mount
   [owner]
   (log owner 2 "did-mount"))

  (will-unmount
   [owner]
   (log owner 2 "will-unmount"))

  (will-update
   [owner _ _]
   (log owner 3 "will-update"))
  (did-update
   [owner _ _]
   (log owner 2 "did-update"))

  (will-receive-props
   [owner _]
   (log owner 3 "will-receive-props")))
