(ns sweet-tooth.frontend.form.components
  (:require [re-frame.core :refer [dispatch-sync dispatch subscribe]]
            [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [cljs-time.format :as tf]
            [cljs-time.core :as ct]
            [sweet-tooth.frontend.paths :as p]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.form.flow :as stff]))

(defn progress-indicator
  "Show a progress indicator when a form is submitted"
  [state]
  (let [state @state]
    [:span
     (cond (= state :submitting)
           [:span.submission-progress "submitting..."]

           (= state :success)
           [:span.submission-progress
            [:i {:class "fa fa-check-circle"}]
            " success"])]))

(defn dispatch-change
  [partial-form-path attr-path val]
  (dispatch-sync [::stff/update-attr-buffer partial-form-path attr-path val]))

(defn dispatch-touch
  [partial-form-path attr-path]
  (dispatch-sync [::stff/touch-attr partial-form-path attr-path val]))

(defn dispatch-validation
  [partial-form-path attr-path validation-fn]
  (dispatch-sync [::stff/update-attr-errors partial-form-path attr-path validation-fn]))

(defn handle-change
  "Meant for input fields, where your keystrokes should update the
  field. Gets new value from event."
  [event partial-form-path attr-path]
  (dispatch-change partial-form-path attr-path (u/tv event)))

(defn attr-path-str
  [attr-path]
  (name (if (vector? attr-path) (last attr-path) attr-path)))

(defn label-text [attr]
  (u/kw-str (attr-path-str attr)))

(defn label-for [form-id attr-path]
  (str form-id (attr-path-str attr-path)))

;;~~~~~~~~~~~~~~~~~~
;; input
;;~~~~~~~~~~~~~~~~~~

;; react doesn't recognize these and hates them
(def custom-opts #{:attr-buffer :attr-path :attr-errors
                   :no-label :options :partial-form-path
                   :format-read :format-write})

(defn dissoc-custom-opts
  [x]
  (apply dissoc x custom-opts))

(defn input-opts
  [{:keys [form-id attr-path attr-buffer partial-form-path format-read format-write]
    :or {format-read identity
         format-write identity}
    :as opts}]
  (-> {:value     (format-read @attr-buffer)
       :id        (label-for form-id attr-path)
       :on-change #(dispatch-change partial-form-path attr-path (format-write (u/tv %)))
       :on-blur   #(dispatch-touch partial-form-path attr-path)
       :class     (str "input " (attr-path-str attr-path))}
      (merge opts)
      (dissoc-custom-opts)))

(defn input-key
  [{:keys [form-id partial-form-path attr-path]} & suffix]
  (str form-id partial-form-path attr-path (str/join "" suffix)))

(defmulti input (fn [type _] type))

(defmethod input :textarea
  [type opts]
  [:textarea (input-opts opts)])

(defmethod input :select
  [type {:keys [options attr-buffer format-read] :as opts}]
  [:select (merge (input-opts opts) {:value (format-read @attr-buffer)})
   (for [[opt-value txt option-opts] options]
     ^{:key (input-key opts opt-value)}
     [:option (cond-> {}
                opt-value (assoc :value opt-value)
                true      (merge option-opts))
      txt])])

(defmethod input :radio
  [type {:keys [options partial-form-path attr-path attr-buffer format-read] :as opts}]
  [:ul.radio
   (doall (for [[v txt] options]
            ^{:key (input-key opts v)}
            [:li [:label
                  [:input (-> opts
                              dissoc-custom-opts
                              (merge {:type "radio"
                                      :checked (= v (format-read @attr-buffer))
                                      :on-change #(dispatch-change partial-form-path attr-path v)}))]
                  [:span txt]]]))])

(defmethod input :checkbox
  [type {:keys [form-id attr-buffer partial-form-path attr-path format-read] :as opts}]
  (let [value (format-read @attr-buffer)
        opts (dissoc (input-opts opts) :value)]
    [:input (merge opts
                   {:type "checkbox"
                    :on-change #(dispatch-change partial-form-path attr-path (not value))
                    :default-checked (boolean value)})]))

(defn toggle-set-membership
  [s v]
  ((if (s v) disj conj) s v))

(defmethod input :checkbox-set
  [type {:keys [form-id attr-buffer partial-form-path attr-path options value format-read] :as opts}]
  (let [checkbox-set (or (format-read @attr-buffer) #{})
        opts (input-opts opts)]
    [:input (-> opts
                dissoc-custom-opts
                (merge {:type "checkbox"
                        :checked (boolean (checkbox-set value))
                        :on-change #(dispatch-change partial-form-path attr-path (toggle-set-membership checkbox-set value))}))]))

;; date handling
(defn unparse [fmt x]
  (if x (tf/unparse fmt (js/goog.date.DateTime. x))))

(def date-fmt (:date tf/formatters))

(defn handle-date-change [e partial-form-path attr-path]
  (let [v (u/tv e)]
    (if (empty? v)
      (dispatch-change partial-form-path attr-path nil)
      (let [date (tf/parse date-fmt v)
            date (js/Date. (ct/year date) (dec (ct/month date)) (ct/day date))]
        (dispatch-change partial-form-path attr-path date)))))

(defmethod input :date
  [type {:keys [form-id attr-buffer partial-form-path attr-path]}]
  [:input {:type "date"
           :value (unparse date-fmt @attr-buffer)
           :id (label-for form-id attr-path)
           :on-change #(handle-date-change % partial-form-path attr-path)}])

(defmethod input :number
  [type {:keys [form-id partial-form-path attr-path] :as opts}]
  [:input (merge (input-opts opts)
                 {:type (name type)
                  :on-change #(let [v (js/parseInt (u/tv  %))]
                                (if (js/isNaN v)
                                  (dispatch-change partial-form-path attr-path nil)
                                  (dispatch-change partial-form-path attr-path (js/parseInt v))))})])

(defmethod input :default
  [type {:keys [form-id] :as opts}]
  [:input (merge (input-opts opts) {:type (name type)})])
;;~~~~~~~~~~~~~~~~~~
;; end input
;;~~~~~~~~~~~~~~~~~~

(defn error-messages
  "A list of error messages"
  [errors]
  (when (seq errors)
    [:ul {:class "error-messages"}
     (map (fn [x] ^{:key (str "error-" x)} [:li x]) errors)]))

(defn field-row
  "Structure the field as a table row"
  [type {:keys [form-id attr-path attr-errors required] :as opts}]
  [:tr {:class (when @attr-errors "error")}
   [:td [:label {:for (label-for form-id attr-path) :class "label"}
         (label-text attr-path)
         (if required [:span {:class "required"} "*"])]]
   [:td [input type opts]
    (error-messages @attr-errors)]])

(defn path->class
  [path]
  (->> path
       (filter (comp not number?))
       (filter identity)
       (map name)
       (str/join " ")))

(defmulti field (fn [type _] type))

(defmethod field :default
  [type {:keys [form-id tip attr-path attr-errors required label no-label
                before-input after-input after-errors] :as opts}]
  [:div.field {:class (str (u/kebab (attr-path-str attr-path)) (when @attr-errors "error"))}
   (when-not no-label
     [:label {:for (label-for form-id attr-path) :class "label"}
      (or label (label-text attr-path))
      (when required [:span {:class "required"} "*"])])
   (when tip [:div.tip tip])
   [:div
    before-input
    [input type (dissoc opts :tip :before-input :after-input :after-errors)]
    after-input
    (error-messages @attr-errors)
    after-errors]])

(defn checkbox-field
  [type {:keys [data form-id tip required label no-label
                attr-path attr-errors]
         :as opts}]
  [:div.field {:class (str (u/kebab (attr-path-str attr-path)) (when @attr-errors "error"))}
   [:div
    (if no-label
      [:span [input type (dissoc opts :tip)] [:i]]
      [:label {:class "label"}
       [input type (dissoc opts :tip)]
       [:i]
       (or label (label-text attr-path))
       (when required [:span {:class "required"} "*"])])
    (when tip [:div.tip tip])
    (error-messages @attr-errors)]])

(defmethod field :checkbox
  [type opts]
  (checkbox-field type opts))

(defmethod field :checkbox-set
  [type opts]
  (checkbox-field type opts))

(defn build-input-opts
  "Merges the input option hierarchy: 

  1. options provided by the framework
  2. options that are meant to be applied to every input for this form
  3. options for this specific input

  `formwide-input-opts` and `input-opts` can be functions, allowing
  them access to the framework opts. This is so that custom input
  handlers like `:on-blur` can have access to the same context as
  framework handlers like `:on-change`."
  [partial-form-path attr-path formwide-input-opts input-opts]
  (let [framework-opts      {:attr-path         attr-path
                             :attr-buffer       (subscribe [::stff/attr-buffer partial-form-path attr-path])
                             :attr-errors       (subscribe [::stff/attr-errors partial-form-path attr-path])
                             :partial-form-path partial-form-path}
        formwide-input-opts (if (fn? formwide-input-opts)
                              (formwide-input-opts framework-opts)
                              formwide-input-opts)
        input-opts          (if (fn? input-opts)
                              (input-opts framework-opts)
                              input-opts)]
    (merge framework-opts formwide-input-opts input-opts)))

(defn builder
  "creates a function (component) that builds inputs"
  [partial-form-path formwide-input-opts]
  (fn [type attr-path & {:as input-opts}]
    [field type (build-input-opts partial-form-path
                                  attr-path
                                  formwide-input-opts
                                  input-opts)]))

(defn on-submit
  [form-path & [submit-opts]]
  {:on-submit (u/prevent-default #(dispatch [::stff/submit-form form-path submit-opts]))})

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [partial-form-path & [opts]]
  {:form-state    (subscribe [::stff/state partial-form-path])
   :form-ui-state (subscribe [::stff/ui-state partial-form-path])
   :form-errors   (subscribe [::stff/errors partial-form-path])
   :form-data     (subscribe [::stff/buffer partial-form-path])
   :form-dirty?   (subscribe [::stff/form-dirty? partial-form-path])
   :input         (builder partial-form-path (:input opts))})

(defn client-side-validation
  "Returns options that you can pass to `:input` for `form`. These
  options, passed to an input, enable client-side validation such that
  validation is triggered on blur and performed on every change
  thereafter."
  [validator]
  (fn [{:keys [partial-form-path attr-path attr-errors format-write]
        :or {format-write identity}
        :as opts}]
    (let [validate #(dispatch-validation partial-form-path attr-path validator)]
      {:on-change #(do (dispatch-change partial-form-path attr-path (format-write (u/tv %)))
                       (when (some? @attr-errors)
                         (validate)))
       :on-blur validate})))
