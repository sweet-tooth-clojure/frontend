(ns demo.components.topic)

(defn component
  []
  (let [topic nil]
    [:div [:h2 (:topic/title topic)]]))
