(ns cochlea.core
  (:use [environ.core :only [env]]
        [seesaw core dev keymap]
        [cochlea.sounds])
  (:require [clojure.java.io :as io]
            [clojure.tools.trace :as trace]
            [cochlea.past :as past]
            [cochlea.instruments :as inst])
  (:gen-class))

(defn display
  [content the-frame]
  (config! the-frame :content content)
  the-frame)
(defn- map-keys
    [frame pairs]
    (doseq [ [key f] pairs]
        (map-key frame key f)))
(defn text-of
    [e]
    (config (to-widget e) :text))
(defn force-play
  ([] (doseq [result (play)]
        (println result)))
  ([mode sound] (doseq [result (play mode sound)]
                    (println result))))
(defn -main
  "There is a 'main' window where the choices, replay button, volume slider, an overtone eval buffer, and options menu are present.
  There is a 'history' window with a query buffer, radio buttons filtering the default query (maybe these should be grayed-out).
  "
  [& args]
  (let [replay-button (button :text "Replay" :listen [:action (fn [a] (force-play (@cache :prev-mode) (@cache :prev-sound)))])
        next-button (button :text "Next" :listen [:action (fn [a] (force-play))])
        volume-slider (slider :id :volume :value 50 :min 0 :max 100
                              :listen [:state-changed (fn [e] (-> e to-widget value set-volume))])
        answer (text :id :answer :text "" :background java.awt.Color/GRAY :halign :center :editable? false)
        [level-group mode-group instrument-group] [(button-group) (button-group) (button-group)];should be mutually exclusive and persist a session.
        draw-hist (fn []
                      (let [vis-group (button-group)
                            query-group (button-group)
                            query-result (styled-text :id :query-result :wrap-lines? true)
                            query-buffer (styled-text :text ((:query-str @past/query))
                                                       ; it should be 'shift-enter', like in ipython
                                                      :listen [:key-typed (fn [e]
                                                                            (let [k (.getKeyChar e)
                                                                                  query (text-of e) ]
                                                                              (when (= k \newline)
                                                                                 (config! query-result :text (past/run-query query)))))])
                            new-frame (frame :title "History"
                                             :menubar (menubar :items
                                                               [ (menu :text "Visualization" :items
                                                                       (for [[qname qdef] @past/queries]
                                                                     (radio-menu-item :group vis-group :text (str qname)
                                                                                      :selected? (= (:id qdef) (:id @past/query))
                                                                                      ;using dosync because these are conceptually a transaction.
                                                                                      :listen [:action (fn [e] (dosync
                                                                                                                (reset! past/query qdef)
                                                                                                                (config! query-buffer :text ((:query-str qdef)))))])))]))
                            visualization-trigger (button :text "visualize" :listen [:action (fn [e] (config! query-result :text (past/visualize)))])]
                        (do
                            (native!)
                            (map-keys new-frame [ ["V" past/visualize] ])
                            (display
                              (border-panel
                               :east (vertical-panel :items [(scrollable query-result) query-buffer
                                                             (text :text (:descr @past/query) :editable? false)
                                                             visualization-trigger])
                                  :center (vertical-panel :items [(text "current-n")
                                                                  (slider :id :current-n-slider :min 3 :max 20 :value 10
                                                                          :snap-to-ticks? true  :paint-track? true :major-tick-spacing 6 :minor-tick-spacing 1 :paint-labels? true
                                                                          :listen [:state-changed (fn [e]
                                                                                                    (swap! past/query-options assoc :current-N (-> e to-widget value )))])
                                                                  (text "window-n")
                                                                  (slider :id :window-n-slider :min 8 :max 100 :value 40
                                                                          :snap-to-ticks? true  :paint-track? true :major-tick-spacing 20 :minor-tick-spacing 3 :paint-labels? true
                                                                          :listen [:state-changed (fn [e]
                                                                                                    (swap! past/query-options assoc :window-N (-> e to-widget value )))])])
                                  :west (vertical-panel :items
                                                (concat
                                                    (for [i (keys choices)]
                                                      (radio :id i :text (str i) :selected? (some #(= i %) (:modes @past/query-options))
                                                               :listen [:item-state-changed (fn [event]
                                                                                              (do
                                                                                                (if (some #(= i %) (:modes @past/query-options))
                                                                                                    (swap! past/query-options assoc :modes (filter #(not= i %) (:modes @past/query-options)))
                                                                                                    (swap! past/query-options assoc :modes (conj (@past/query-options :modes) i)))
                                                                                                (config! query-buffer :text ((:query-str @past/query)))))]))
                                                    (for [i levels]
                                                        (radio :id i :text (str i) :selected? (some #(= i %)  (:levels @past/query-options))
                                                               :listen [:item-state-changed (fn [event]
                                                                                              (do
                                                                                                (if (some #(= i %)  (:levels @past/query-options))
                                                                                                  (swap! past/query-options assoc :levels (filter #(not= i %) (:levels @past/query-options)))
                                                                                                  (swap! past/query-options assoc :levels (conj (@past/query-options :levels) i)))
                                                                                                (config! query-buffer :text ((:query-str @past/query)))))])))))
                              new-frame
                              ))))
        build-choices (fn []
                        (let [choice-group (button-group)
                              correct-choice? (fn [choice] (= (@cache :prev-sound) choice))]
                            (for [i (get-in choices [(@opts :practice-mode) (@opts :level)])]
                                (radio :id i :group choice-group :text (name i)
                                       :listen [:mouse-entered   (fn [e] (config! e :foreground java.awt.Color/BLUE))
                                                :mouse-exited    (fn [e] (config! e :foreground java.awt.Color/BLACK))
                                                :mouse-released  (fn [e] (let [choice (text-of e)
                                                                              correct? (correct-choice? (keyword choice)) ]
                                                                          (dorun
                                                                            [(past/dbstore opts cache correct?)
                                                                             (config! answer :text (str "correct answer is: " (:prev-sound @cache)))
                                                                             (if correct?
                                                                               (do (config! e :foreground java.awt.Color/GREEN)
                                                                                   (config! answer :foreground java.awt.Color/GREEN))
                                                                               (do (config! e :foreground java.awt.Color/RED)
                                                                                   (config! answer :foreground java.awt.Color/RED)))
                                                                             (when (:autoplay env)
                                                                                   (force-play))])))]))))
        draw-main (fn []
         (let [toggle-item (menu-item :id :toggle :text "Toggle Fullscreen")
               choice-panel (vertical-panel :id "choice-panel" :items (build-choices))
               new-frame (frame :title "cochlea"
                                :on-close :exit
                                ;:icon (io/file "/Users/shalom/projects/cochlea/resources/spiral.jpg")
                                :menubar (menubar :items [(menu :text "Level" :items
                                                               (for [i (map name levels)]
                                                                 (radio-menu-item :group level-group :text i :selected? (= (keyword i) (@opts :level))
                                                                                  :listen [:mouse-released
                                                                                           (fn [e] (do (swap! opts assoc :level (-> e text-of keyword))
                                                                                                      (config! choice-panel :items (build-choices))
                                                                                                      (repaint! choice-panel)))])))
                                                          (menu :text "Mode" :items
                                                               (for [i (keys choices)]
                                                                 (radio-menu-item :group mode-group :text (name i)
                                                                                  :selected? (= (keyword i) (@opts :practice-mode))
                                                                                  :listen [:mouse-released
                                                                                           (fn [e]
                                                                                             (do (swap! opts assoc :practice-mode (-> e text-of keyword))
                                                                                                 (config! choice-panel :items (build-choices))
                                                                                                 (repaint! choice-panel)
                                                                                                 ))])))
                                                          (menu :text "Views" :items [toggle-item
                                                                                      (menu-item :id :history :text "Performance History"
                                                                                                 :listen [:action (fn [e] (-> (draw-hist) pack! show!))])])
                                                         (menu :text "Instruments" :items
                                                               (for [i inst/instruments]
                                                                 (radio-menu-item :group instrument-group :text (inst/nameof i)
                                                                                  :selected? (= i (@opts :instrument))
                                                                                  :listen [:mouse-released (fn [e] (do
                                                                                                                    (swap! opts assoc :instrument (ns-resolve 'cochlea.instruments (-> e text-of symbol)))))])))
                                                         ]))]
          (do
            (native!)
            (listen toggle-item :action (fn [e] (toggle-full-screen! new-frame)))
            (map-keys new-frame [["control F" (fn [e] (toggle-full-screen! new-frame))]
                                 ["R" replay-button]
                                 ["N" next-button]
                                 ["H" #(-> (draw-hist) pack! show!)]])
            (force-play)
            (display (border-panel :west (scrollable choice-panel)
                                   :center (vertical-panel :items [replay-button next-button volume-slider answer])
                                   :vgap 5 :hgap 5 :border 5)
                     new-frame))))]
    (when (and (> (count args) 0) (not (= (:db-choice env) "")))
      (when (= (first args) "init")
        (past/establish-db)))
    (when-not (= :db-choice "")
      (past/tap-db))
    (-> (draw-main) pack! show!)))
