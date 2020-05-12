(ns sweet-tooth.frontend.form.components)

(defn form-components-form
  [path body]
  (if (map? (first body))
    `(form-components ~path ~(first body))
    `(form-components ~path)))

(defn form-body
  [body]
  (if (map? (first body))
    (rest body)
    body))

(defmacro with-form
  [partial-form-path & body]
  (let [path (gensym)]
    `(let [~path ~partial-form-path
           {:keys [~'form-path
                   ~'form-state
                   ~'form-ui-state
                   ~'form-errors
                   ~'form-buffer
                   ~'form-dirty?

                   ~'state-success?

                   ~'sync-state
                   ~'sync-active?
                   ~'sync-success?
                   ~'sync-fail?]
            :as ~'form-subs}
           (form-subs ~path)]
       (let [{:keys [~'on-submit
                     ~'on-submit-handler
                     ~'input-opts
                     ~'input
                     ~'field]
              :as   ~'form-components}
             ~(form-components-form path body)
             ~'form (merge ~'form-subs ~'form-components)]
         ~@(form-body body)))))
