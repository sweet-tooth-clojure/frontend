(ns sweet-tooth.frontend.form.components
  (:require [re-frame.core :refer [dispatch-sync dispatch subscribe]]
            [reagent.core :refer [atom] :as r]
            [clojure.string :as s]
            [cljs-time.format :as tf]
            [cljs-time.core :as ct]
            [sweet-tooth.frontend.core.utils :as u]
            [sweet-tooth.frontend.core.handlers :as stch]
            [sweet-tooth.frontend.form.handlers :as stfh]))

(defn form-path
  [partial-path]
  (into [:forms] partial-path))

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
  [dk attr-name val]
  (dispatch-sync [::stch/assoc-in (conj dk :data attr-name) val]))

(defn handle-change*
  [v dk attr-name]
  (dispatch-change dk attr-name v))

(defn handle-change
  "Meant for input fields, where your keystrokes should update the
  field"
  [e dk attr-name]
  (handle-change* (u/tv e) dk attr-name))

(defn label-for [form-id attr-name]
  (str form-id (name attr-name)))

;;~~~~~~~~~~~~~~~~~~
;; input
;;~~~~~~~~~~~~~~~~~~
(defn input-opts
  [{:keys [data form-id dk attr-name placeholder] :as opts}]
  (merge opts
         {:value (get-in @data [:data attr-name])
          :id (label-for form-id attr-name)
          :on-change #(handle-change % dk attr-name)
          :class (str "input " (name attr-name))}))

(defmulti input (fn [type _] type))

(defmethod input :textarea
  [type opts]
  [:textarea (input-opts opts)])

(defmethod input :select
  [type {:keys [data options dk attr-name] :as opts}]
  (let [value (get-in @data [:data attr-name])]
    [:select (merge (input-opts opts) {:value value})
     (for [[v txt] options]
       ^{:key (gensym)}
       [:option {:value v} txt])]))

(defmethod input :radio
  [type {:keys [data options dk attr-name] :as opts}]
  (let [value (get-in @data [:data attr-name])]
    [:ul.radio
     (for [[v txt] options]
       ^{:key (gensym)}
       [:li [:label
             [:input (merge opts
                            {:type "radio"
                             :checked (= v value)
                             :on-change #(handle-change* v dk attr-name)})]
             [:span txt]]])]))

(defmethod input :checkbox
  [type {:keys [data form-id dk attr-name] :as opts}]
  (let [value (get-in @data [:data attr-name])
        opts (input-opts opts)]
    [:input (merge opts
                   {:type "checkbox"
                    :checked value
                    :on-change #(handle-change* (not value) dk attr-name)})]))

(defn toggle-set-membership
  [s v]
  ((if (s v) disj conj) s v))

(defmethod input :checkbox-set
  [type {:keys [data form-id dk attr-name options value] :as opts}]
  (let [checkbox-set (or (get-in @data [:data attr-name]) #{})
        opts (input-opts opts)]
    [:input (merge opts
                   {:type "checkbox"
                    :checked (checkbox-set value)
                    :on-change #(handle-change* (toggle-set-membership checkbox-set value) dk attr-name)})]))

(defn error-messages
  [errors]
  (when (seq errors)
    [:ul {:class "error-messages"}
     (map (fn [x] ^{:key (gensym)} [:li x]) errors)]))

;; date handling
(defn unparse [fmt x]
  (if x (tf/unparse fmt (js/goog.date.DateTime. x))))

(def date-fmt (:date tf/formatters))

(defn handle-date-change [e dk attr-name]
  (let [v (u/tv e)]
    (if (empty? v)
      (dispatch-change dk attr-name nil)
      (let [date (tf/parse date-fmt v)
            date (js/Date. (ct/year date) (dec (ct/month date)) (ct/day date))]
        (dispatch-change dk attr-name date)))))

(defmethod input :date
  [type {:keys [data form-id dk attr-name]}]
  [:input {:type "date"
           :value (unparse date-fmt (get-in @data [:data attr-name]))
           :id (label-for form-id attr-name)
           :on-change #(handle-date-change % dk attr-name)}])

(defmethod input :number
  [type {:keys [data form-id dk attr-name placeholder] :as opts}]
  [:input (merge opts
                 {:type (name type)
                  :id (label-for form-id attr-name)
                  :value (get-in @data [:data attr-name])
                  :on-change #(let [v (js/parseInt (u/tv  %))]
                                (if (js/isNaN v)
                                  (handle-change* nil dk attr-name)
                                  (handle-change* (js/parseInt v) dk attr-name)))})])

(defmethod input :default
  [type {:keys [data form-id dk attr-name placeholder] :as opts}]
  [:input (merge opts
                 {:type (name type)
                  :id (label-for form-id attr-name)
                  :value (get-in @data [:data attr-name])
                  :on-change #(handle-change % dk attr-name)})])
;;~~~~~~~~~~~~~~~~~~
;; end input
;;~~~~~~~~~~~~~~~~~~

(defn field-row [type {:keys [data form-id attr-name required] :as opts}]
  (let [errors (get-in @data [:errors attr-name])]
    [:tr {:class (when errors "error")}
     [:td [:label {:for (label-for form-id attr-name) :class "label"}
           (label-text attr-name)
           (if required [:span {:class "required"} "*"])]]
     [:td [input type opts]
      (error-messages errors)]]))

(defn path->class
  [path]
  (->> path
       (filter (comp not number?))
       (filter identity)
       (map name)
       (apply str)))

(defmulti field (fn [type _] type))

(defmethod field :default
  [type {:keys [data form-id tip dk attr-name required label no-label] :as opts}]
  (let [errors (get-in @data [:errors attr-name])]
    [:div.field {:class (str (u/kebab (name attr-name)) (when errors "error"))}
     (when-not no-label
       [:label {:for (label-for form-id attr-name) :class "label"}
        (or label (label-text attr-name))
        (when required [:span {:class "required"} "*"])])
     (when tip [:div.tip tip])
     [:div {:class (str (path->class dk) " " (name attr-name))}
      [input type (dissoc opts :tip)]
      (error-messages errors)]]))

(defn checkbox-field
  [type {:keys [data form-id tip dk attr-name required label no-label] :as opts}]
  (let [errors (get-in @data [:errors attr-name])]
    [:div.field {:class (str (u/kebab (name attr-name)) (when errors "error"))}
     [:div {:class (str (path->class dk) " " (name attr-name))}
      (if no-label
        [:span [input type (dissoc opts :tip)] [:i]]
        [:label {:class "label"}
         [input type (dissoc opts :tip)]
         [:i]
         (or label (label-text attr-name))
         (when required [:span {:class "required"} "*"])])
      (when tip [:div.tip tip])
      (error-messages errors)]]))

(defmethod field :checkbox
  [type opts]
  (checkbox-field type opts))

(defmethod field :checkbox-set
  [type opts]
  (checkbox-field type opts))

(defn builder
  "creates a function that builds inputs"
  [path]
  (let [path (form-path path)
        data (subscribe (into [:key] path))]
    (fn [type attr-name & {:as opts}]
      [field type (merge {:data data
                          :dk path
                          :attr-name attr-name}
                         opts)])))

(defn on-submit
  [form-path & [spec]]
  {:on-submit (u/prevent-default #(dispatch [::stfh/submit-form form-path spec]))})

(defn form-errors
  [path]
  (let [errors (subscribe (u/flatv :key (form-path path) :errors))]
    (fn []
      (error-messages (-> @errors
                          (select-keys [:authorization :authentication])
                          vals)))))

(defn form
  [form-path]
  {:form-state (subscribe [:form-state form-path])
   :ui-state (subscribe [:form-ui-state form-path])
   :input (builder form-path)})
