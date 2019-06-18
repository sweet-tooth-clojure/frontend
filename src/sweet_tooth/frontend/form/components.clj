(ns sweet-tooth.frontend.form.components)

(defmacro with-form
  [partial-form-path & body]
  (let [form-path         'form-path
        form-state        'form-state
        form-ui-state     'form-ui-state
        form-errors       'form-errors
        form-buffer       'form-buffer
        form-dirty?       'form-dirty?
        on-submit         'on-submit
        on-submit-handler 'on-submit-handler
        input-opts        'input-opts
        input             'input
        field             'field
        form              'form]
    `(let [{:keys [~form-path
                   ~form-state
                   ~form-ui-state
                   ~form-errors
                   ~form-buffer
                   ~form-dirty?
                   ~on-submit
                   ~on-submit-handler
                   ~input-opts
                   ~input
                   ~field]
            :as   ~form}
           (form ~partial-form-path)]
       ~@body)))

