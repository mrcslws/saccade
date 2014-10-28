(defproject com.mrcslws/saccade "NOT_DEPLOYED"
  :description "Simulates saccades and processes them with HTM"
  :url "http://mrcslws.com/saccade"
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2371"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [ring "1.3.1"]
                 [compojure "1.2.0"]
                 [enlive "1.1.5"]
                 [om "0.7.3"]
                 [figwheel "0.1.4-SNAPSHOT"]
                 [environ "1.0.0"]
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.0-SNAPSHOT"]
                 [leiningen "2.5.0"]
                 [com.cognitect/transit-cljs "0.8.188"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-environ "1.0.0"]
            [com.cemerick/clojurescript.test "0.3.1"]]

  :min-lein-version "2.5.0"

  :uberjar-name "saccade.jar"

  :cljsbuild {:builds
              {:app {:source-paths ["src/cljs"]
                     :compiler {:output-to "resources/public/js/app.js"
                                :output-dir "resources/public/js/out"
                                :source-map "resources/public/js/out.js.map"
                                :preamble ["react/react.min.js"]
                                :externs ["react/externs/react.js"]
                                :optimizations :none
                                :pretty-print  true}}
               :test {:source-paths ["src/cljs" "test/cljs"]
                      :compiler {:output-to "target/classes/test/tests.js"
                                 :output-dir "target/classes/test/script"
                                 :source-map "target/classes/test/tests.js.map"
                                 :preamble ["react/react.min.js"]
                                 :externs ["react/externs/react.js"]
                                 :optimizations :none
                                 :pretty-print true}}}
              :test-commands {"unit-tests" ["phantomjs" :runner
                                            "this.literal_js_was_evaluated=true"
                                            "target/classes/test/tests.js"]}}

  :profiles {:dev {:repl-options
                   {:init-ns saccade.server
                    :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                   :plugins [[lein-figwheel "0.1.4-SNAPSHOT"]
                             [cider/cider-nrepl "0.7.0"]]
                   :figwheel {:http-server-root "public"
                              :port 3449
                              :css-dirs ["resources/public/css"]}
                   :env {:is-dev true}
                   :cljsbuild {:builds
                               {:app {:source-paths ["env/dev/cljs"]}}}}
             :uberjar {:hooks [leiningen.cljsbuild]
                       :env {:production true}
                       :omit-source true
                       :aot :all
                       :cljsbuild {:builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                             {:optimizations :advanced
                                              :pretty-print false}}}}}})
