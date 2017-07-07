(ns sweet-tooth.frontend.paths)

(def form-prefix :sweet-tooth.frontend.form)
(defn full-form-path
  [partial-path]
  (into [form-prefix] partial-path))

(def partial-path (comp vec rest))

(def page-prefix :sweet-tooth.frontend.page)
