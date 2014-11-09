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
