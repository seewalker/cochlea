(ns cochlea.instruments
  ^{:author "Alex Seewald"
    :doc "Provides a var 'instruments' which is a list of overtone instruments."}
    (:use [overtone.inst.piano]
          [overtone.inst.synth]
          [overtone.live]
          [environ.core :only [env]])
    (:require [philharmonia-samples.sampled-mandolin :refer [mandolin-inst]]
              [philharmonia-samples.sampled-guitar :refer [guitar-inst]]))
(definst steel-drum  [note 60 amp 0.8]
  (let  [freq  (midicps note)]
      (* amp
         (env-gen  (perc) 1 1 0 1 :action FREE)
         (+  (sin-osc  (/ freq 2))
             (rlpf  (saw freq)  (* 1.1 freq) 0.4)))))
(definst equal-sin-saw [note 60 amp 0.8 sustain 3]
  (let [freq (midicps note)
        enve (env-gen (lin 0.04 sustain))]
    (* amp
       enve
    (apply + (map #(+  (* (second %) (saw (* freq (first %))))
                       (* (second %) (sin-osc (* freq (first %)))))
                   [ [1.0 1.0] [2.0 0.6] [3.02 0.7]])))))

(definst equal-sin-saw-dist [note 60 amp 0.8 sustain 3]
  (let [freq (midicps note)
        enve (env-gen (lin 0.04 sustain))]
    (* amp enve
       (apply + (map #(+  (* (second %) (saw (* freq (first %))))
                          (* (second %) (sin-osc (* freq (first %)))))
                      [ [1.0 1.0] [2.0 0.6] [3.02 0.7]])))))
(inst-fx! equal-sin-saw-dist fx-distortion)

(definst equal-sin-saw-echo [note 60 amp 0.8 sustain 3]
  (let [freq (midicps note)
        enve (env-gen (lin 0.04 sustain))]
    (* amp enve
       (apply + (map #(+  (* (second %) (saw (* freq (first %))))
                          (* (second %) (sin-osc (* freq (first %)))))
                      [ [1.0 1.0] [2.0 0.6] [3.02 0.7]])))))
(inst-fx! equal-sin-saw-echo fx-echo)

(definst equal-sin-saw-chorus [note 60 amp 0.8 sustain 3]
  (let [freq (midicps note)
        enve (env-gen (lin 0.04 sustain))]
    (* amp enve
       (apply + (map #(+  (* (second %) (saw (* freq (first %))))
                          (* (second %) (sin-osc (* freq (first %)))))
                      [ [1.0 1.0] [2.0 0.6] [3.02 0.7]])))))
(definst alarm [note 60 harshness 0.2]
  (let [freq (midicps note)
        enve (env-gen (perc 0.01 2.0))]
    (* enve
       (* 4.0 (clip2 (apply + [(sin-osc freq) (sin-osc (* freq 1.3))]) harshness)))))
(inst-fx! equal-sin-saw-chorus fx-chorus)

(defcgen oinst
  [note {:default 60}
   overtones {:default [ [1.0 1.0]]}]
  (:ar
   (let [freq (midicps note)]
      (clip2  (map #(* (second %) (sin-osc (* freq (first %)))) overtones)0.8))))

;sounds like the low-pass filter lets all lower noise go through
;sounds like the bps lets noise around a certain point go through.
(definst noise-band [note 60 amt 0.4 noise-filt 7000]
    (let [freq (midicps note)
          noise (bpf (white-noise) noise-filt 0.5)
          enve (env-gen (lin))]
        (* enve (+ (sin-osc freq) (* amt noise)))))
(definst noise-low [note 60 amt 0.4 noise-filt 1000]
    (let [freq (midicps note)
          noise (rlpf (white-noise) noise-filt)
          enve (env-gen (lin))]
        (* enve (+ (sin-osc freq) (* amt noise)))))
(definst noise-high [note 60 amt 0.4 noise-filt 7000]
    (let [freq (midicps note)
          noise (rhpf (white-noise) noise-filt)
          enve (env-gen (lin))]
        (* enve (+ (sin-osc freq) (* amt noise)))))

(def sampled-insts { mandolin-inst "mandolin"
                     guitar-inst "guitar"})
(def mandolin mandolin-inst)
(def guitar guitar-inst)
(def overtone-insts [piano steel-drum ping noise-low noise-high bass])
(defn nameof
  [inst]
  (cond
   (some #(= inst %) overtone-insts) (:name inst)
   (contains? sampled-insts inst) (get sampled-insts inst)
   true "absence error"))
(def instruments
  (vec (concat overtone-insts (keys sampled-insts))))
