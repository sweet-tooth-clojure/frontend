(set-env!
  :source-paths   #{"src" "../src"}
  :resource-paths #{"resources"}
  :target-path    "target/build"
  :dependencies   '[[org.clojure/clojure         "1.9.0"    :scope "provided"]
                    [org.clojure/clojurescript   "1.10.439" :scope "provided"]
                    [re-frame                    "0.10.5"   :scope "provided"]
                    [adzerk/bootlaces            "0.1.13"   :scope "test"]
                    [adzerk/boot-test            "1.1.1"    :scope "test"]
                    [cljs-ajax                   "0.6.0"]
                    [meta-merge                  "1.0.0"]
                    [com.andrewmcveigh/cljs-time "0.4.0"]
                    [com.taoensso/timbre         "4.10.0"]
                    [com.cemerick/url            "0.1.1"]
                    [integrant                   "0.8.0-alpha2"]

                    
                    [adzerk/boot-cljs        "RELEASE" :scope "test"]
                    [adzerk/boot-reload      "RELEASE" :scope "test"]
                    [pandeiro/boot-http      "RELEASE" :scope "test"]
                    [flyingmachine/boot-sass "0.2.2-SNAPSHOT" :scope "test"]
                    [binaryage/devtools      "0.9.4"          :scope "test"]
                    [day8.re-frame/re-frame-10x "0.2.0"       :scope "test"]
                    [venantius/accountant       "0.2.4"]
                    [reifyhealth/specmonstah "2.0.0-alpha-1" :scope "test"]
                    [org.clojure/test.check "0.9.0" :scope "test"]
                    [aysylu/loom "1.0.2"]
                    [funcool/bide "1.6.0"]])

(require
  '[adzerk.boot-cljs      :refer [cljs]]
  '[adzerk.boot-reload    :refer [reload]]
  '[pandeiro.boot-http    :refer [serve]])

(deftask demo
  []
  (task-options!
    cljs {:compiler-options {:asset-path      "/main.out"
                             :closure-defines {"goog.DEBUG"                          true
                                               "re_frame.trace.trace_enabled_QMARK_" true}
                             :main            "demo.core"
                             :parallel-build  true
                             :aot-cache       true
                             :preloads        '[devtools.preload
                                                day8.re-frame-10x.preload]}}
    reload {:on-jsload 'demo.core/-main})
  
  (comp (serve :dir "target/demo")
        (watch)
        (reload)
        (cljs)
        (target :dir #{"target/demo"})))
