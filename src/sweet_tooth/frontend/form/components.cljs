(ns sweet-tooth.frontend.form.components
  (:require [re-frame.core :as rf]
            [clojure.string :as str]
            [cljs-time.format :as tf]
            [cljs-time.core :as ct]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.form.flow :as stff])
  (:require-macros [sweet-tooth.frontend.form.components]))

(defn dispatch-input-event
  [event {:keys [format-write] :as input-opts} & [update-val?]]
  (rf/dispatch-sync [::stff/input-event (cond-> (select-keys input-opts [:partial-form-path
                                                                         :attr-path
                                                                         :validate])
                                          true        (merge {:event-type (u/go-get event ["type"])})
                                          update-val? (merge {:val (format-write (u/tv event))}))]))

(defn dispatch-new-val
  "Helper when you want non-input elemnts to update a val"
  [form-path attr-path val & [opts]]
  (rf/dispatch-sync [::stff/input-event (merge {:partial-form-path form-path
                                                :attr-path         attr-path
                                                :event-type        nil
                                                :val               val}
                                               opts)]))

(defn attr-path-str
  [attr-path]
  (name (if (vector? attr-path) (last attr-path) attr-path)))

(defn label-text [attr]
  (u/kw-str (attr-path-str attr)))

(defn label-for [form-id attr-path]
  (str form-id (attr-path-str attr-path)))

(defn input-key
  [{:keys [form-id partial-form-path attr-path]} & suffix]
  (str form-id partial-form-path attr-path (str/join "" suffix)))

;; composition helpers
(defn pre-wrap
  [f1 f2]
  (fn [& args]
    (apply f2 args)
    (apply f1 args)))

(defn post-wrap
  [f1 f2]
  (fn [& args]
    (apply f1 args)
    (apply f2 args)))

;;~~~~~~~~~~~~~~~~~~
;; input opts
;;~~~~~~~~~~~~~~~~~~

;; used in the field component below
(def field-opts #{:tip :before-input :after-input :after-errors :label :no-label :show-errors-on})

;; react doesn't recognize these and hates them
(def input-opts #{:attr-buffer :attr-path :attr-errors :attr-visible-errors :attr-input-events
                  :label :no-label :options
                  :partial-form-path :input-type
                  :format-read :format-write
                  :validate})

(defn dissoc-input-opts
  [x]
  (apply dissoc x input-opts))

(defn dissoc-field-opts
  [x]
  (apply dissoc x field-opts))


(defn framework-input-opts
  [{:keys [partial-form-path attr-path show-errors-on] :as opts
    :or   {show-errors-on #{"blur" "submit" "show-errors"}}}]
  (merge {:attr-buffer         (rf/subscribe [::stff/attr-buffer partial-form-path attr-path])
          :attr-errors         (rf/subscribe [::stff/attr-errors partial-form-path attr-path])
          :attr-visible-errors (rf/subscribe [::stff/attr-visible-errors
                                              partial-form-path
                                              attr-path
                                              show-errors-on])
          :attr-input-events   (rf/subscribe [::stff/attr-input-events partial-form-path attr-path])}
         opts))

(defn input-type-opts-default
  [{:keys [form-id attr-path attr-buffer input-type]
    :as   opts}]
  (let [{:keys [format-read] :as opts} (merge {:format-read identity
                                               :format-write identity}
                                              opts)]
    (merge {:type         (name (or input-type :text))
            :value        (format-read @attr-buffer)
            :id           (label-for form-id attr-path)
            :on-change    #(dispatch-input-event % opts true)
            :on-blur      #(dispatch-input-event % opts false)
            :on-focus     #(dispatch-input-event % opts false)
            :class        (str "input " (attr-path-str attr-path))}
           opts)))

(defmulti input-type-opts :input-type)

(defmethod input-type-opts :default
  [opts]
  (input-type-opts-default opts))

(defmethod input-type-opts :textarea
  [opts]
  (-> (input-type-opts-default opts)
      (dissoc :type)))

(defmethod input-type-opts :select
  [opts]
  (-> (input-type-opts-default opts)
      (dissoc :type)))

(defmethod input-type-opts :radio
  [{:keys [format-read format-write attr-buffer value] :as opts}]
  (let [format-read  (or format-read identity)
        format-write (or format-write (constantly value))]
    (assoc (input-type-opts-default (merge opts {:format-write format-write}))
           :checked (= value (format-read @attr-buffer)))))

(defmethod input-type-opts :checkbox
  [{:keys [attr-buffer format-read format-write] :as opts}]
  (let [format-read  (or format-read identity)
        value        (format-read @attr-buffer)
        format-write (or format-write (constantly (not value)))]
    (-> (input-type-opts-default opts)
        (merge {:default-checked (boolean value)
                :on-change       #(dispatch-input-event % (merge opts {:format-write format-write}) true)})
        (dissoc :value))))

(defn toggle-set-membership
  [s v]
  (let [new-s ((if (s v) disj conj) s v)]
    (if (empty? new-s) #{} new-s)))

(defmethod input-type-opts :checkbox-set
  [{:keys [attr-buffer value format-read format-write] :as opts}]
  (let [format-read  (or format-read identity)
        checkbox-set (or (format-read @attr-buffer) #{})
        format-write (or format-write (constantly (toggle-set-membership checkbox-set value)))]
    (merge (input-type-opts-default opts)
           {:type      "checkbox"
            :checked   (boolean (checkbox-set value))
            :on-change #(dispatch-input-event % (merge opts {:format-write format-write}) true)})))

;; date handling
(defn unparse [fmt x]
  (when x (tf/unparse fmt (js/goog.date.DateTime. x))))

(def date-fmt (:date tf/formatters))

(defn format-write-date
  [v]
  (if (empty? v)
    nil
    (let [parsed (tf/parse date-fmt v)]
      (js/Date. (ct/year parsed) (dec (ct/month parsed)) (ct/day parsed)))))

(defmethod input-type-opts :date
  [{:keys [attr-buffer] :as opts}]
  (assoc (input-type-opts-default opts)
         :value (unparse date-fmt @attr-buffer)
         :on-change #(dispatch-input-event % (merge {:format-write format-write-date} opts) true)))

(defn format-write-number
  [v]
  (let [parsed (js/parseInt v)]
    (if (js/isNaN parsed) nil parsed)))

(defmethod input-type-opts :number
  [opts]
  (assoc (input-type-opts-default opts)
         :on-change #(dispatch-input-event % (merge {:format-write format-write-number} opts) true)))

;;~~~~~~~~~~~~~~~~~~
;; input components
;;~~~~~~~~~~~~~~~~~~

(defmulti input :input-type)

(defmethod input :textarea
  [opts]
  [:textarea (dissoc-input-opts opts)])

(defmethod input :select
  [{:keys [options]
    :as   opts}]
  [:select (dissoc-input-opts opts)
   (for [[opt-value txt option-opts] options]
     ^{:key (input-key opts opt-value)}
     [:option (cond-> {}
                opt-value (assoc :value opt-value)
                true      (merge option-opts))
      txt])])

(defmethod input :default
  [opts]
  [:input (dissoc-input-opts opts)])

;;~~~~~~~~~~~~~~~~~~
;; 'field' interface, wraps inputs with error messagse and labels
;;~~~~~~~~~~~~~~~~~~

(defn error-class
  [error-sub]
  (when (seq @error-sub) " error"))

(defn error-messages
  "A list of error messages"
  [error-sub]
  (let [errors @error-sub]
    (when (seq errors)
      [:ul {:class "error-messages"}
       (map (fn [x] ^{:key (str "error-" x)} [:li x]) errors)])))

(defmulti field :input-type)

(defmethod field :default
  [{:keys [form-id attr-path attr-visible-errors
           tip required label no-label
           before-input after-input after-errors]
    :as opts}]
  [:div.field {:class (str (u/kebab (attr-path-str attr-path))
                           (error-class attr-visible-errors))}
   (when-not no-label
     [:label {:for (label-for form-id attr-path) :class "label"}
      (or label (label-text attr-path))
      (when required [:span {:class "required"} "*"])])
   (when tip [:div.tip tip])
   [:div
    before-input
    [input (dissoc-field-opts opts)]
    after-input
    (error-messages attr-visible-errors)
    after-errors]])

(defn checkbox-field
  [{:keys [tip required label no-label attr-path attr-visible-errors]
    :as opts}]
  [:div.field {:class (str (u/kebab (attr-path-str attr-path))
                           (error-class attr-visible-errors))}
   [:div
    (if no-label
      [:span [input (apply dissoc opts field-opts)] [:i]]
      [:label {:class "label"}
       [input (dissoc-field-opts opts)]
       [:i]
       (or label (label-text attr-path))
       (when required [:span {:class "required"} "*"])])
    (when tip [:div.tip tip])
    (error-messages attr-visible-errors)]])

(defmethod field :checkbox
  [opts]
  (checkbox-field opts))

(defmethod field :checkbox-set
  [opts]
  (checkbox-field opts))


(defn field-component
  "Adapts the interface to `field` so that the caller can supply either
  a) a map of opts as the only argument or b) an `input-type`,
  `attr-path`, and `input-opts`.

  In the case of b, `input-opts` consists only of the opts specific to
  this input (it doesn't include framework opts). Those opts are
  passed to the `input-opts` function.

  This allows the user to call [input :text :user/username {:x :y}]
  rather than something like

  [input (all-input-opts :partial-form-path :text :user/username {:x :y})]"
  [all-input-opts-fn]
  (fn [input-type & [attr-path input-opts]]
    [field (if (map? input-type)
             input-type
             (all-input-opts-fn input-type attr-path input-opts))]))

;;~~~~~~~~~~~~~~~~~~
;; interface fns
;;~~~~~~~~~~~~~~~~~~

(defn all-input-opts
  [partial-form-path formwide-input-opts input-type attr-path & [opts]]
  (-> {:partial-form-path partial-form-path
       :input-type        input-type
       :attr-path         attr-path}
      (merge formwide-input-opts)
      (merge opts)
      (framework-input-opts)
      (input-type-opts)))

(defn input-component
  "Adapts the interface to `input` so that the caller can supply either
  a) a map of opts as the only argument or b) an `input-type`,
  `attr-path`, and `input-opts`.

  In the case of b, `input-opts` consists only of the opts specific to
  this input (it doesn't include framework opts). Those opts are
  passed to the `input-opts` function.

  This allows the user to call [input :text :user/username {:x :y}]
  rather than something like

  [input (all-input-opts :partial-form-path :text :user/username {:x :y})]"
  [all-input-opts-fn]
  (fn [input-type & [attr-path input-opts]]
    [input (if (map? input-type)
             input-type
             (all-input-opts-fn input-type attr-path input-opts))]))

(defn on-submit-handler
  [partial-form-path & [submit-opts]]
  (u/prevent-default #(rf/dispatch [::stff/submit-form partial-form-path submit-opts])))

(defn on-submit
  [partial-form-path & [submit-opts]]
  {:on-submit (on-submit-handler partial-form-path submit-opts)})

(defn form-subs
  [partial-form-path]
  {:form-path     partial-form-path
   :form-state    (rf/subscribe [::stff/state partial-form-path])
   :form-ui-state (rf/subscribe [::stff/ui-state partial-form-path])
   :form-errors   (rf/subscribe [::stff/errors partial-form-path])
   :form-buffer   (rf/subscribe [::stff/buffer partial-form-path])
   :form-dirty?   (rf/subscribe [::stff/form-dirty? partial-form-path])

   :state-success? (rf/subscribe [::stff/state-success? partial-form-path])

   :sync-state    (rf/subscribe [::stff/sync-state partial-form-path])
   :sync-active?  (rf/subscribe [::stff/sync-active? partial-form-path])
   :sync-success? (rf/subscribe [::stff/sync-success? partial-form-path])
   :sync-fail?    (rf/subscribe [::stff/sync-fail? partial-form-path])})

(defn form-components
  [partial-form-path & [formwide-input-opts]]
  (let [input-opts-fn (partial all-input-opts partial-form-path formwide-input-opts)]
    {:on-submit         (partial on-submit partial-form-path)
     :on-submit-handler (partial on-submit-handler partial-form-path)
     :input-opts        input-opts-fn
     :input             (input-component input-opts-fn)
     :field             (field-component input-opts-fn)}))

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [partial-form-path & [formwide-input-opts]]
  (merge (form-subs partial-form-path)
         (form-components partial-form-path formwide-input-opts)))
