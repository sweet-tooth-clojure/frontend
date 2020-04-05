(set-env!
 :source-paths #{"src"}
 :target-path  "target/build"
 :dependencies '[[adzerk/bootlaces "0.1.13" :scope "test"]
                 [seancorfield/boot-tools-deps "0.4.7" :scope "test"]
                 [crisptrutski/boot-cljs-test "0.3.5-SNAPSHOT"]])

(require
 '[adzerk.bootlaces :as bootlaces]
 '[boot-tools-deps.core :refer [deps]]
 '[crisptrutski.boot-cljs-test :refer [test-cljs]])

(def +version+ "0.12.1")
(bootlaces/bootlaces! +version+)

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
  (comp (#'adzerk.bootlaces/collect-clojars-credentials)
        (push :file           file
              :tag            (boolean #'adzerk.bootlaces/+last-commit+)
              :gpg-sign       false
              :ensure-release true
              :repo           "deploy-clojars")))

(deftask build-jar
  ""
  []
  (comp (deps :overwrite-boot-deps true :aliases [:cljs])
        (bootlaces/build-jar)))


(deftask test
  "runs tests"
  []
  (comp (deps :aliases [:cljs])
        (merge-env! :source-paths #{"test"})
        (test-cljs)))
