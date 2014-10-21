(defproject com.mrcslws/saccade "NOT_DEPLOYED"
  :description "Simulates saccades and processes them with HTM"
  :url "http://mrcslws.com/saccade"
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2342"]
                 [ring "1.2.2"]
                 [compojure "1.1.8"]
                 [enlive "1.1.5"]
                 [com.cognitect/transit-cljs "0.8.188"]]

  :profiles {:dev {:repl-options {:init-ns saccade.server}
                   :plugins [[com.cemerick/austin "0.1.5"]
                             [lein-cljsbuild "1.0.3"]
                             [cider/cider-nrepl "0.7.0"]
                             [com.cemerick/clojurescript.test "0.3.1"]]
                   :cljsbuild {:builds [{:source-paths ["src/cljs"]
                                         :compiler {
                                                    :output-dir "target/classes/public/script"
                                                    :output-to "target/classes/public/app.js"
                                                    :optimizations :simple
                                                    :pretty-print true
                                                    :source-map "target/classes/public/app.js.map"}}
                                        {:source-paths ["src/cljs" "test/cljs"]
                                         :compiler {
                                                    :output-dir "target/classes/test/script"
                                                    :output-to "target/classes/test/tests.js"
                                                    :optimizations :simple
                                                    :pretty-print true
                                                    :source-map "target/classes/test/tests.js.map"}}]
                               :test-commands {"unit-tests" ["phantomjs" :runner
                                                             "this.literal_js_was_evaluated=true"
                                                             "target/classes/test/tests.js"]}}}})
