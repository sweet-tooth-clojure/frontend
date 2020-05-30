(defproject sweet-tooth/sweet-tooth-frontend "0.13.1-SNAPSHOT"
  :description "Some opinions on top of re-frame"
  :url         "https://github.com/sweet-tooth-clojure/sweet-tooth-frontend"
  :license     {:name "MIT"
                :url  "https://opensource.org/licenses/MIT"}

  :plugins [[lein-tools-deps "0.4.5"]
            [lein-doo "0.1.10"]]

  :deploy-repositories [["releases" :clojars]]

  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]

  :lein-tools-deps/config {:config-files [:install :user :project]}

  :cljsbuild {:builds [{:id           "test"
                        :source-paths ["src" "test"]
                        :compiler     {:output-to     "target/testable.js"
                                       :main          sweet-tooth.cljs-test-runner
                                       :optimizations :none
                                       :target        :nodejs}}]}

  :doo {:build "test"
        :alias {:default [:node]}})
