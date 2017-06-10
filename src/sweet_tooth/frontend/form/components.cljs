(ns sweet-tooth.frontend.form.components
  (:require [re-frame.core :refer [dispatch-sync dispatch subscribe]]
            [reagent.core :refer [atom] :as r]
            [clojure.string :as str]
            [cljs-time.format :as tf]
            [cljs-time.core :as ct]
            [sweet-tooth.frontend.paths :as p]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core.handlers :as stch]
            [sweet-tooth.frontend.form.handlers :as stfh]))

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
  [attr-path val]
  (dispatch-sync [::stch/assoc-in attr-path val]))

(defn handle-change*
  [v attr-path]
  (dispatch-change attr-path v))

(defn handle-change
  "Meant for input fields, where your keystrokes should update the
  field. Gets new value from event."
  [e attr-path]
  (dispatch-change attr-path (u/tv e)))

(defn label-for [form-id attr-name]
  (str form-id (name attr-name)))

;;~~~~~~~~~~~~~~~~~~
;; input
;;~~~~~~~~~~~~~~~~~~

;; react doesn't recognize these and hates them
(def custom-opts #{:attr-path :attr-val :attr-name :attr-errors :no-label})

(defn dissoc-custom-opts
  [x]
  (apply dissoc x custom-opts))

(defn input-opts
  [{:keys [form-id placeholder attr-name attr-path attr-val] :as opts}]
  (-> opts
      (merge {:value @attr-val
              :id (label-for form-id attr-name)
              :on-change #(handle-change % attr-path)
              :class (str "input " (name attr-name))})
      (dissoc-custom-opts)))

(defmulti input (fn [type _] type))

(defmethod input :textarea
  [type opts]
  [:textarea (input-opts opts)])

(defmethod input :select
  [type {:keys [options attr-val] :as opts}]
  [:select (merge (input-opts opts) {:value @attr-val})
   (for [[v txt] options]
     ^{:key (gensym)}
     [:option {:value v} txt])])

(defmethod input :radio
  [type {:keys [options attr-val attr-path] :as opts}]
  [:ul.radio
   (for [[v txt] options]
     ^{:key (gensym)}
     [:li [:label
           [:input (merge (dissoc-custom-opts opts)
                          {:type "radio"
                           :checked (= v @attr-val)
                           :on-change #(handle-change* v attr-path)})]
           [:span txt]]])])

(defmethod input :checkbox
  [type {:keys [form-id attr-val attr-path] :as opts}]
  (let [value @attr-val
        opts (dissoc (input-opts opts) :value)]
    [:input (merge opts
                   {:type "checkbox"
                    :on-change #(handle-change* (not value) attr-path)
                    :default-checked (boolean value)})]))

(defn toggle-set-membership
  [s v]
  ((if (s v) disj conj) s v))

(defmethod input :checkbox-set
  [type {:keys [form-id attr-val attr-path options value] :as opts}]
  (let [checkbox-set (or @attr-val #{})
        opts (input-opts opts)]
    [:input (merge opts
                   {:type "checkbox"
                    :checked (boolean (checkbox-set value))
                    :on-change #(handle-change* (toggle-set-membership checkbox-set value) attr-path)})]))

;; date handling
(defn unparse [fmt x]
  (if x (tf/unparse fmt (js/goog.date.DateTime. x))))

(def date-fmt (:date tf/formatters))

(defn handle-date-change [e attr-path]
  (let [v (u/tv e)]
    (if (empty? v)
      (dispatch-change attr-path nil)
      (let [date (tf/parse date-fmt v)
            date (js/Date. (ct/year date) (dec (ct/month date)) (ct/day date))]
        (dispatch-change attr-path date)))))

(defmethod input :date
  [type {:keys [form-id attr-path attr-val attr-name]}]
  [:input {:type "date"
           :value (unparse date-fmt @attr-val)
           :id (label-for form-id attr-name)
           :on-change #(handle-date-change % attr-path)}])

(defmethod input :number
  [type {:keys [form-id placeholder attr-path] :as opts}]
  [:input (merge (input-opts opts)
                 {:type (name type)
                  :on-change #(let [v (js/parseInt (u/tv  %))]
                                (if (js/isNaN v)
                                  (handle-change* nil attr-path)
                                  (handle-change* (js/parseInt v) attr-path)))})])

(defmethod input :default
  [type {:keys [form-id placeholder] :as opts}]
  [:input (merge (input-opts opts) {:type (name type)})])
;;~~~~~~~~~~~~~~~~~~
;; end input
;;~~~~~~~~~~~~~~~~~~

(defn error-messages
  "A list of error messages"
  [errors]
  (when (seq errors)
    [:ul {:class "error-messages"}
     (map (fn [x] ^{:key (gensym)} [:li x]) errors)]))

(defn field-row [type {:keys [form-id attr-name attr-errors required] :as opts}]
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

(defn builder
  "creates a function (component) that builds inputs"
  [partial-form-path]
  (let [full-form-path (p/full-form-path partial-form-path)]
    (fn [type attr-name & {:as opts}]
      (let [attr-path (into full-form-path [:data attr-name])
            attr-val (subscribe (u/flatv :key attr-path))
            attr-errors (subscribe (u/flatv :key full-form-path [:errors attr-name]))]
        (fn [type attr-name & {:as opts}]
          [field type (merge {:attr-val attr-val
                              :attr-path attr-path
                              :attr-name attr-name
                              :attr-errors attr-errors}
                             opts)])))))

(defn on-submit
  [form-path & [spec]]
  {:on-submit (u/prevent-default #(dispatch [::stfh/submit-form form-path spec]))})

(defn form
  "Returns an input builder function and subscriptions to all the form's keys"
  [partial-form-path]
  (let [form-attr-path (fn [suffix] (u/flatv :key (p/full-form-path partial-form-path) suffix))]
    {:form-state    (subscribe (form-attr-path :state))
     :form-ui-state (subscribe (form-attr-path :ui-state))
     :form-errors   (subscribe (form-attr-path :errors))
     :form-data     (subscribe (form-attr-path :data)) 
     :input         (builder partial-form-path)}))
