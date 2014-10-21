(ns saccade.macros)

(defmacro set-prefixed! [atom unprefixed]
  `(do
     (set! ~atom (str "-moz-" ~unprefixed))
     (set! ~atom (str "-webkit-" ~unprefixed))
     (set! ~atom (str "-ms-" ~unprefixed))
     (set! ~atom ~unprefixed)))
