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
        
        sync-state    'sync-state
        sync-active?  'sync-active
        sync-success? 'sync-success?
        sync-fail?    'sync-fail?
        
        form 'form]
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
                   ~field

                   ~sync-state
                   ~sync-active?
                   ~sync-success?
                   ~sync-fail?]
            :as   ~form}
           (form ~partial-form-path)]
       ~@body)))

(defmacro with-entity-form
  [partial-form-path entity & body]
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

        sync-state    'sync-state
        sync-active?  'sync-active
        sync-success? 'sync-success?
        sync-fail?    'sync-fail?
        
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
                   ~field

                   ~sync-state
                   ~sync-active?
                   ~sync-success?
                   ~sync-fail?]
            :as   ~form}
           (form ~partial-form-path ~entity)]
       ~@body)))
