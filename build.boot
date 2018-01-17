(set-env!
  :source-paths   #{"src"}
  :target-path    "target/build"
  :dependencies   '[[org.clojure/clojure         "1.9.0-alpha16" :scope "provided"]
                    [org.clojure/clojurescript   "1.9.456"       :scope "provided"]
                    [re-frame                    "0.10.2"         :scope "provided"]
                    [adzerk/bootlaces            "0.1.13"        :scope "test"]
                    [adzerk/boot-test            "1.1.1"         :scope "test"]
                    [cljs-ajax                   "0.6.0"]
                    [com.andrewmcveigh/cljs-time "0.4.0"]
                    [com.taoensso/timbre         "4.10.0"]
                    [com.cemerick/url            "0.1.1"]])

(require
 '[adzerk.bootlaces :refer :all]
 '[adzerk.boot-test :refer :all])

(def +version+ "0.2.9")
(bootlaces! +version+)

(task-options!
 pom  {:project     'sweet-tooth/sweet-tooth-frontend
       :version     +version+
       :description "Some opinions on top of re-frame"
       :url         "https://github.com/sweet-tooth-clojure/sweet-tooth-frontend"
       :scm         {:url "https://github.com/sweet-tooth-clojure/sweet-tooth-frontend"}
       :license     {"MIT" "https://opensource.org/licenses/MIT"}})


(deftask push-release-without-gpg
  "Deploy release version to Clojars without gpg signature."
  [f file PATH str "The jar file to deploy."]
  (comp
    (#'adzerk.bootlaces/collect-clojars-credentials)
    (push
      :file           file
      :tag            (boolean #'adzerk.bootlaces/+last-commit+)
      :gpg-sign       false
      :ensure-release true
      :repo           "deploy-clojars")))
