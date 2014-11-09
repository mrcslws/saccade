(ns saccade.macros)

(defmacro go-monitor [chn donemult args & body]
  `(let [done# (cljs.core.async/chan)]
     (cljs.core.async/tap ~donemult done#)
     (cljs.core.async.macros/go-loop []
       (cljs.core.async.macros/alt!
         ~chn
         (~args
          ~@body
          (recur))

         done#
         :goodbye))))

;; Implemented as a macro because alts!! isn't available in ClojureScript,
;; so we're forced to use alt! in a (go ...) block, so we need a macro to
;; use the caller's (go ...) block.
(defmacro drain! [chn]
  `(loop []
    (cljs.core.async.macros/alt!
      ~chn ([_] (recur))
      :default :channels-are-drained)))
