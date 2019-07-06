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
  
  (let [form-path     'form-path
        form-state    'form-state
        form-subs     'form-subs
        form-ui-state 'form-ui-state
        form-errors   'form-errors
        form-buffer   'form-buffer
        form-dirty?   'form-dirty?
        
        state-success? 'state-success?
        
        sync-state    'sync-state
        sync-active?  'sync-active?
        sync-success? 'sync-success?
        sync-fail?    'sync-fail?

        on-submit         'on-submit
        on-submit-handler 'on-submit-handler
        input-opts        'input-opts
        input             'input
        field             'field

        form-state      'form-state
        form-components 'form-components
        form            'form

        path (gensym)]
    `(let [~path ~partial-form-path
           {:keys [~form-path
                   ~form-state
                   ~form-ui-state
                   ~form-errors
                   ~form-buffer
                   ~form-dirty?

                   ~state-success?

                   ~sync-state
                   ~sync-active?
                   ~sync-success?
                   ~sync-fail?]
            :as ~form-subs}
           (form-subs ~path)]
       (let [{:keys [~on-submit
                     ~on-submit-handler
                     ~input-opts
                     ~input
                     ~field]
              :as   ~form-components}
             ~(form-components-form path body)
             ~form (merge ~form-subs ~form-components)]
         ~@(form-body body)))))
