(set-env!
  :source-paths   #{"src"}
  :target-path    "target/build"
  :dependencies   '[[adzerk/bootlaces "0.1.13" :scope "test"]
                    [adzerk/boot-test "1.1.1"  :scope "test"]
                    [seancorfield/boot-tools-deps "0.4.7" :scope "test"]])

(require
  '[adzerk.bootlaces :as bootlaces]
  '[adzerk.boot-test :as boot-test]
  '[boot-tools-deps.core :refer [deps]])

(def +version+ "0.6.2")
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
  (comp (deps :overwrite-boot-deps true) (bootlaces/build-jar)))
