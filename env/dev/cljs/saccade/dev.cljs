(ns saccade.dev
    (:require [saccade.main]
        [figwheel.client :as figwheel :include-macros true]
        [cljs.core.async :refer [put!]]
        [weasel.repl :as weasel]))

(enable-console-print!)
(set! *print-newline* true)

(figwheel/watch-and-reload
    :websocket-url "ws://localhost:3449/figwheel-ws"
    :jsload-callback (fn [] (saccade.main/render-loop)))

(weasel/connect "ws://localhost:9001" :verbose true :print #{:repl :console})

(saccade.main/render-loop)
