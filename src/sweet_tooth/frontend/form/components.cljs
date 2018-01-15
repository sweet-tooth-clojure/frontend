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

(defn label-text [attr]
  (u/kw-str attr))

(defn dispatch-change
  [partial-form-path attr-name val]
  (dispatch-sync [::stff/update-attr partial-form-path attr-name val]))

(defn handle-change
  "Meant for input fields, where your keystrokes should update the
  field. Gets new value from event."
  [event partial-form-path attr-name]
  (dispatch-change partial-form-path attr-name (u/tv event)))

(defn label-for [form-id attr-name]
  (str form-id (name attr-name)))

;;~~~~~~~~~~~~~~~~~~
;; input
;;~~~~~~~~~~~~~~~~~~

;; react doesn't recognize these and hates them
(def custom-opts #{:attr-val :attr-name :attr-errors :no-label :options :partial-form-path})

(defn dissoc-custom-opts
  [x]
  (apply dissoc x custom-opts))

(defn input-opts
  [{:keys [form-id attr-name attr-val partial-form-path] :as opts}]
  (-> opts
      (merge {:value @attr-val
              :id (label-for form-id attr-name)
              :on-change #(handle-change % partial-form-path attr-name)
              :class (str "input " (name attr-name))})
      (dissoc-custom-opts)))

(defn input-key
  [{:keys [form-id partial-form-path attr-name]} & suffix]
  (str form-id partial-form-path attr-name (str/join "" suffix)))

(defmulti input (fn [type _] type))

(defmethod input :textarea
  [type opts]
  [:textarea (input-opts opts)])

(defmethod input :select
  [type {:keys [options attr-val] :as opts}]
  [:select (merge (input-opts opts) {:value @attr-val})
   (for [[v txt] options]
     ^{:key (input-key opts v)}
     [:option {:value v} txt])])

(defmethod input :radio
  [type {:keys [options partial-form-path attr-name attr-val] :as opts}]
  [:ul.radio
   (doall (for [[v txt] options]
            ^{:key (input-key opts v)}
            [:li [:label
                  [:input (-> opts
                              dissoc-custom-opts
                              (merge {:type "radio"
                                      :checked (= v @attr-val)
                                      :on-change #(dispatch-change partial-form-path attr-name v)}))]
                  [:span txt]]]))])

(defmethod input :checkbox
  [type {:keys [form-id attr-val partial-form-path attr-name] :as opts}]
  (let [value @attr-val
        opts (dissoc (input-opts opts) :value)]
    [:input (merge opts
                   {:type "checkbox"
                    :on-change #(dispatch-change partial-form-path attr-name (not value))
                    :default-checked (boolean value)})]))

(defn toggle-set-membership
  [s v]
  ((if (s v) disj conj) s v))

(defmethod input :checkbox-set
  [type {:keys [form-id attr-val partial-form-path attr-name options value] :as opts}]
  (let [checkbox-set (or @attr-val #{})
        opts (input-opts opts)]
    [:input (-> opts
                dissoc-custom-opts
                (merge {:type "checkbox"
                        :checked (boolean (checkbox-set value))
                        :on-change #(dispatch-change partial-form-path attr-name (toggle-set-membership checkbox-set value))}))]))

;; date handling
(defn unparse [fmt x]
  (if x (tf/unparse fmt (js/goog.date.DateTime. x))))

(def date-fmt (:date tf/formatters))

(defn handle-date-change [e partial-form-path attr-name]
  (let [v (u/tv e)]
    (if (empty? v)
      (dispatch-change partial-form-path attr-name nil)
      (let [date (tf/parse date-fmt v)
            date (js/Date. (ct/year date) (dec (ct/month date)) (ct/day date))]
        (dispatch-change partial-form-path attr-name date)))))

(defmethod input :date
  [type {:keys [form-id attr-val partial-form-path attr-name]}]
  [:input {:type "date"
           :value (unparse date-fmt @attr-val)
           :id (label-for form-id attr-name)
           :on-change #(handle-date-change % partial-form-path attr-name)}])

(defmethod input :number
  [type {:keys [form-id partial-form-path attr-name] :as opts}]
  [:input (merge (input-opts opts)
                 {:type (name type)
                  :on-change #(let [v (js/parseInt (u/tv  %))]
                                (if (js/isNaN v)
                                  (dispatch-change partial-form-path attr-name nil)
                                  (dispatch-change partial-form-path attr-name (js/parseInt v))))})])

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
  [type {:keys [form-id attr-name attr-errors required] :as opts}]
  [:tr {:class (when @attr-errors "error")}
   [:td [:label {:for (label-for form-id attr-name) :class "label"}
         (label-text attr-name)
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
  [type {:keys [form-id tip attr-name attr-path attr-errors required label no-label] :as opts}]
  [:div.field {:class (str (u/kebab (name attr-name)) (when @attr-errors "error"))}
   (when-not no-label
     [:label {:for (label-for form-id attr-name) :class "label"}
      (or label (label-text attr-name))
      (when required [:span {:class "required"} "*"])])
   (when tip [:div.tip tip])
   [:div {:class (path->class attr-path)}
    [input type (dissoc opts :tip)]
    (error-messages @attr-errors)]])

(defn checkbox-field
  [type {:keys [data form-id tip required label no-label
                attr-name attr-path attr-errors]
         :as opts}]
  [:div.field {:class (str (u/kebab (name attr-name)) (when @attr-errors "error"))}
   [:div {:class (path->class attr-path)}
    (if no-label
      [:span [input type (dissoc opts :tip)] [:i]]
      [:label {:class "label"}
       [input type (dissoc opts :tip)]
       [:i]
       (or label (label-text attr-name))
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
  [framework-opts formwide-input-opts input-opts]
  (let [formwide-input-opts (if (fn? formwide-input-opts)
                              (formwide-input-opts framework-opts)
                              formwide-input-opts)
        input-opts          (if (fn? input-opts)
                              (input-opts framework-opts)
                              input-opts)]
    (merge framework-opts formwide-input-opts input-opts)))

(defn builder
  "creates a function (component) that builds inputs"
  [partial-form-path formwide-input-opts]
  (fn [type attr-name & {:as input-opts}]
    (let [attr-val    (subscribe [::stff/form-attr-data partial-form-path attr-name])
          attr-errors (subscribe [::stff/form-attr-errors partial-form-path attr-name])]
      [field type (build-input-opts {:attr-val          attr-val
                                     :attr-name         attr-name
                                     :attr-errors       attr-errors
                                     :partial-form-path partial-form-path}
                                    formwide-input-opts
                                    input-opts)])))

(defn on-submit
  [form-path & [submit-opts]]
  {:on-submit (u/prevent-default #(dispatch [::stff/submit-form form-path submit-opts]))})

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [partial-form-path & [opts]]
  {:form-state    (subscribe [::stff/state partial-form-path])
   :form-ui-state (subscribe [::stff/ui-state partial-form-path])
   :form-errors   (subscribe [::stff/errors partial-form-path])
   :form-data     (subscribe [::stff/data partial-form-path])
   :form-dirty?   (subscribe [::stff/form-dirty? partial-form-path])
   :input         (builder partial-form-path (:input opts))})
