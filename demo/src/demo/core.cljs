(ns demo.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            ; ["react-transition-group/TransitionGroup" :as TransitionGroup]
            ; ["react-transition-group/Transition" :as Transition]
            ; ["react-transition-group/CSSTransition" :as CSSTransition]
            [sweet-tooth.frontend.form.flow :as stff] ;; prep handlers for registration
            [sweet-tooth.frontend.core :as st-core]
            [sweet-tooth.frontend.core.utils :as stcu]
            [sweet-tooth.frontend.core.flow :as stcf]
            [sweet-tooth.frontend.routes.flow :as strf]
            [sweet-tooth.frontend.form.components :as stfc]
            [goog.events])
  (:import [goog.events EventType]))
