(set-env!
  :source-paths   #{"src"}
  :target-path    "target/build"
  :dependencies   '[[org.clojure/clojure         "1.9.0-alpha12" :scope "provided"]
                    [org.clojure/clojurescript   "1.9.456"       :scope "provided"]
                    [re-frame                    "0.9.1"         :scope "provided"]
                    [adzerk/bootlaces            "0.1.13"        :scope "test"]
                    [adzerk/boot-test            "1.1.1"         :scope "test"]
                    [cljs-ajax                   "0.5.8"]
                    [com.rpl/specter             "0.13.2"]
                    [com.andrewmcveigh/cljs-time "0.4.0"]])

(require
 '[adzerk.bootlaces :refer :all]
 '[adzerk.boot-test :refer :all])

(def +version+ "0.1.0-SNAPSHOT")
(bootlaces! +version+)

(task-options!
 pom  {:project     'sweet-tooth/sweet-tooth-frontend
       :version     +version+
       :description "Some opinions on top of re-frame"
       :url         "https://github.com/sweet-tooth-clojure/sweet-tooth-frontend"
       :scm         {:url "https://github.com/sweet-tooth-clojure/sweet-tooth-frontend"}
       :license     {"MIT" "https://opensource.org/licenses/MIT"} })

(deftask make-install
  "local install"
  []
  (comp (pom)
        (jar)
        (install)))
