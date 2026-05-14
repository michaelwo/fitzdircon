# S22i ML Discovery

## Context

The S22i camera and microphone are accessible through normal Android APIs, but the hardware is modest:

- Camera: single front-facing `1600x1200` Camera2 `LIMITED` device.
- Microphone: basic embedded mono capture path through `AudioRecord`.

That makes high-fidelity video coaching or rich audio analysis unlikely to be the right first product idea. The more interesting question is whether these weak media signals can add Garmin-like inferred value: derived rider state, fatigue, effort, and context signals that are not directly available from the bike telemetry alone.

## Initial ML Ideas

The first set of ideas focused on direct rider-facing features:

- Rider presence and readiness detection.
- Form and posture nudges.
- Fatigue or effort-state estimation.
- Breathing intensity as an exertion proxy.
- Voice commands.
- Safety or distress detection.
- Automatic ride annotation.
- Adaptive coaching.

These are technically plausible, especially because they only need coarse camera/audio signals. However, many of them feel uncompelling as standalone product features. They risk being shallow versions of what a wearable already infers more robustly from heart rate, movement, and long-term personal baselines.

## Better Framing

The stronger framing is not “use the camera and mic to coach the rider.” It is:

> Use media as weak extra sensors that produce derived features, then combine those features with ride telemetry and heart-rate data to infer rider state.

This is closer to how Garmin-like metrics work. The raw sensor is simple, but value comes from lagged features, baselines, trend modeling, and personalization.

Potential derived signals:

- Rider present / absent.
- Seated / standing.
- Torso sway or instability.
- Head-down duration.
- Breathing intensity.
- Voice/breath strain.
- Audio environment quality: fan/music/noise dominance.
- Effort transition: recovery, sustainable work, strain, fade, or blow-up.

These should be logged as features, not treated as final answers.

## Driver-Monitoring Analogy

Modern cars increasingly use driver-monitoring systems to detect attention, distraction, and drowsiness. That model family is relevant because it uses many of the same low-level visual signals that may matter here:

- face presence;
- head pose;
- gaze direction;
- eye openness and blink behavior;
- mouth opening or yawning;
- face occlusion;
- temporal changes in attention or alertness.

The analogy also clarifies an important product boundary. In a car, active interruption can be justified because the safety risk is immediate. On an indoor bike, real-time visual critique would likely feel annoying, judgmental, or gimmicky.

This project should therefore default to:

- **Live:** only safety-oriented gating, such as rider absent or clear emergency conditions.
- **Post-ride:** critique, fatigue markers, route/workout fit, visible strain, and form durability.

The same signal can feel very different depending on timing. “You look tired” during a ride is nagging. “This is where visible fatigue began” after a ride is analysis.

## Post-Ride Signal Fusion Thesis

The more compelling opportunity is post-ride analysis, not live coaching. A rider can combine:

- native S22i telemetry: cadence, resistance, incline, watts, speed, and control state;
- Garmin activity exports: heart rate, zones, activity timing, and workout execution data;
- planned Garmin workout intent: interval structure, target zones, and training goal;
- Zwift route context: terrain, distance, elevation profile, and known climb segments;
- optional S22i camera/mic-derived features: posture, seated/standing state, torso stability, breathing intensity, and audio environment.

Garmin already does sophisticated inference outdoors. It has body-worn and environmental sensors such as GPS, altimeter, barometer, compass, accelerometer, and gyroscope. During structured workouts, Garmin can also score workout execution against planned targets.

The S22i has a different advantage indoors:

> It can provide a private, fixed third-person view of the rider while also knowing the exact machine workload.

That third-person view may reveal execution quality that HR, watts, and cadence do not fully explain.

## Garmin Comparison

Garmin is strong at inside-out measurement:

- cardiovascular response;
- location and elevation outdoors;
- body-worn motion;
- workout target compliance;
- long-term personal baselines.

But Garmin usually cannot see:

- whether the rider looked composed or strained;
- whether posture changed before performance dropped;
- whether breathing intensity rose before heart rate caught up;
- whether the rider was seated, standing, rocking, head-down, or unstable;
- whether visible form breakdown coincided with route terrain or interval difficulty.

This creates a useful distinction:

- Garmin can say whether the physiological and target metrics matched the plan.
- S22i post-ride analysis might explain **how the rider looked and sounded while executing that plan**.

## Existing Product Landscape

Peloton appears to be the closest adjacent commercial product, but not the same product thesis.

Peloton has camera-based ML features under Peloton Guide / Peloton IQ:

- movement-tracking camera;
- rep tracking;
- real-time form feedback for strength workouts;
- self-view / mirror-like feedback;
- strength benchmarking over repeated workouts;
- voice commands;
- personalized recommendations that may incorporate connected health data.

This validates the broad category: consumer fitness products can use camera/mic ML for training feedback. But Peloton's public feature set appears focused on strength movement tracking and real-time guidance. It does not appear to be a post-ride cycling execution review that combines planned workout intent, route context, bike telemetry, Garmin physiology, and media-derived strain.

iFIT appears further away from this thesis. Public iFIT features emphasize heart-rate and workload personalization:

- ActivePulse adjusts workout intensity from heart-rate zones.
- SmartAdjust adapts workouts based on user changes and history.
- AI Coach / recommendations use goals, history, health data, and session metrics.
- Completed workouts can be exported in formats such as GPX, TCX, CSV, and KML.

Public iFIT material does not show camera-based form analysis, microphone-based breathing/strain analysis, or post-ride visual fatigue review. This also matches the local package findings: installed `com.ifit.rivendell` and `com.ifit.glassos_service` did not request `CAMERA` or `RECORD_AUDIO`.

The open space is therefore:

> Use private media-derived features for post-ride cycling analysis, not real-time strength coaching or generic HR-based personalization.

## Private Third-Person Training Analysis

There is an existing real-world behavior around recording workouts. Gym influencers and some serious lifters/runners/cyclists record themselves to review technique or share training. In public gyms, that has social and privacy friction:

- setting up a phone is awkward;
- the act can look performative or vain;
- other people may be captured;
- even serious training review can be misread as content creation;
- many riders would avoid recording themselves in public despite knowing video can be useful.

A basement S22i setup changes the social context:

- the rider is alone;
- nobody else is in frame;
- the camera is fixed and already part of the machine;
- recording can be automatic, private, and non-performative;
- unflattering or vulnerable moments can be reviewed without public exposure.

The race-photo analogy is useful. Professional race photos often capture athletes at awkward or unflattering moments, but those photos can also reveal fatigue, posture collapse, asymmetry, tension, or form breakdown. Indoors, the S22i could provide a private and repeatable version of that external observer.

The product tone should avoid body critique, aesthetics, shame, or influencer-style content. It should frame the media as private training evidence:

- “Where did execution degrade?”
- “What changed when fatigue appeared?”
- “Was this route-workout pairing too costly?”
- “What should change next time?”

## Coaching Precedent

A useful supporting anecdote is a running coach filming athletes indoors with an iPad for a short hallway gait review. The value was not content creation or vanity. It was diagnostic external evidence.

That kind of review can reveal form issues the athlete cannot feel. A runner may be in excellent fitness and still be surprised by visible heel striking, posture, asymmetry, or technique breakdown. The lesson is that **fitness and form are different signals**.

The S22i camera could provide a private indoor-cycling version of that external observer:

- the rider can feel strong but still show visible compensation;
- workout targets can be completed with poor execution quality;
- fatigue may appear as posture or motion degradation before performance drops;
- evidence is more persuasive when paired with the exact workload and route context.

The product pattern should resemble a coach reviewing film after the effort:

1. Ride first.
2. Analyze after.
3. Show evidence.
4. Connect it to workload, route, and plan.
5. Suggest one or two adjustments.

This strengthens the thesis that camera evidence has training value when it is private, purposeful, and reviewed after the session.

## Segment Review Precedent

Climbing gyms provide another useful analogy. Climbers often record their sends or attempts, and some gyms make phone stands available because video review is normalized in that subculture.

The acceptability comes from purpose:

- climbing attempts are discrete and reviewable;
- body position, foot placement, hip movement, and sequencing matter;
- the video can support technique review;
- the clip can also serve as proof of completion or progress;
- recording is tied to the attempt, not to generalized self-display.

This suggests a better framing for S22i media capture:

> Review the key efforts, not the whole ride as content.

The indoor-cycling equivalents of a climbing attempt are:

- planned intervals;
- Zwift climbs;
- failed or compromised segments;
- best-executed segments;
- worst-executed segments;
- unexpected strain segments;
- missing expected strain segments.

This points to a practical UX: store clips, stills, or derived feature summaries around meaningful ride segments instead of presenting the feature as continuous self-filming. The default should remain private, with export or sharing only as an explicit rider choice.

## Valuable Post-Ride Questions

### Did the ride achieve the intended training stimulus?

Compare the Garmin workout goal, Zwift route profile, S22i machine workload, and Garmin physiological response.

Example output:

> This was planned as tempo, but the route climbs pushed you into threshold twice. The ride became a mixed tempo/over-under session rather than steady aerobic work.

### Was the Zwift route a good match for the workout?

Some Zwift routes are poor matches for steady work because terrain creates spikes. Others are useful for over/under or climbing workouts.

Example output:

> This route was poorly matched for Zone 2. The second-half climbs caused repeated power and HR spikes, so the execution miss was partly route-driven rather than purely rider fatigue.

### Where did visible fatigue begin?

Use media-derived features to locate the first point where the rider changed externally:

- cadence variability increased;
- posture became less stable;
- head position dropped;
- breathing intensity rose;
- resistance or power compliance fell later.

Example output:

> Visible fatigue likely began around minute 38. Breathing intensity and torso motion increased before cadence dropped and heart rate peaked.

### Was the miss caused by fitness, pacing, route, setup, or execution?

Potential post-ride explanations:

- under-recovered: HR high for normal workload early;
- pacing error: early work above plan followed by fade;
- route mismatch: terrain created unavoidable spikes;
- setup/control mismatch: S22i resistance/incline mapping made the planned effort uneven;
- execution issue: cadence instability, posture breakdown, or long coasting gaps;
- environmental issue: fan/music/noise or breathing patterns suggest heat/noise stress.

### Did expected strain actually appear?

This is the inverse of fatigue detection and may be one of the most useful cases. If the Garmin workout plan and Zwift route imply a hard segment, the analysis should expect some combination of:

- rising heart rate with normal lag;
- increased breathing intensity;
- more deliberate cadence;
- more braced posture;
- higher visible strain;
- recovery behavior after the effort.

If the expected strain does not appear, that can mean:

- the rider under-executed the segment;
- the workout is now too easy for the rider;
- the S22i resistance/incline mapping did not create enough load;
- the Garmin target was set too low;
- the route segment looked hard on paper but did not translate into actual workload.

Example output:

> The planned threshold block did not produce threshold-like strain. HR stayed below target, breathing intensity remained low, and posture stayed relaxed. This segment likely under-delivered the intended stimulus.

This can also be positive:

> This same workload produced visible strain last month. Today it did not, suggesting improved tolerance or an easier-than-needed target.

### What were the “ugly race photo” moments?

Instead of judging appearance, identify moments where execution visibly degraded:

- posture collapsed;
- torso sway increased;
- head dropped;
- breathing spiked;
- cadence fell;
- route grade or resistance rose;
- HR drifted or lagged.

The value is not “you looked bad.” The value is:

> This is where the ride got costly.

## Candidate Metrics

More compelling metric names:

- **Execution Quality:** how well the ride matched the planned workout and route constraints.
- **Composure:** how stable and controlled the rider looked during target work.
- **Form Durability:** how long posture and motion stayed consistent before degradation.
- **Visible Fatigue Onset:** the first sustained external sign of fatigue.
- **Route-Workout Fit:** whether the chosen Zwift route supported or sabotaged the planned workout.
- **Workload Tolerance:** how today’s visible/audio strain compared with prior rides at similar HR and workload.

These should be treated as exploratory labels until validated against real rides.

## Expected Strain vs Observed Strain

The clearest product concept is a post-ride comparison between expected and observed strain.

Expected strain comes from:

- the Garmin workout plan;
- the Zwift route profile;
- known climbs or hard segments;
- planned interval intensity;
- S22i target workload.

Observed strain comes from:

- Garmin heart rate response;
- S22i watts, cadence, resistance, incline, and speed;
- breathing intensity;
- posture stability;
- head/torso changes;
- seated/standing state;
- visible facial or body strain where available.

Interpretation matrix:

| Expected | Observed | Possible Interpretation |
|---|---|---|
| High | High | Intended stimulus likely achieved. |
| High | Low | Under-executed, workout too easy, or control/route mapping failed. |
| Low | High | Fatigue, poor recovery, overheating, illness, or route mismatch. |
| Low | Low | Easy/recovery segment likely executed correctly. |

This framing is stronger than generic fatigue detection because it works in both directions:

- detects unexpected fatigue;
- detects missing effort;
- detects fitness improvement;
- detects plan/route mismatch;
- detects machine-control mismatch.

## Relevant Hugging Face Directions

### Audio Event Classification

Hugging Face has broad support for audio classification models, including YAMNet-style sound event models. These can inspire or seed classifiers for:

- quiet room,
- fan noise,
- music-dominant audio,
- normal breathing,
- heavy breathing,
- speech,
- coughing,
- abnormal equipment noise.

Relevant references:

- <https://huggingface.co/tasks/audio-classification>
- <https://huggingface.co/STMicroelectronics/yamnet>

### Voice Strain And Speech Embeddings

Speech-emotion models should not be trusted literally during exercise, but wav2vec2-style embeddings may still capture useful vocal strain, breathiness, or speaking difficulty.

Possible experiment:

- Ask the rider for a short spoken check-in such as “I’m good.”
- Extract audio embeddings and simple acoustic features.
- Correlate them with heart rate, watts, cadence, and subjective post-ride effort.

Relevant references:

- <https://huggingface.co/speechbrain/emotion-recognition-wav2vec2-IEMOCAP>
- <https://huggingface.co/models?pipeline_tag=audio-classification>

### Pose, Action, And Video Classification

Generic action-recognition models are not directly cycling-aware, but the technique is useful for short rider-state clips:

- seated steady,
- standing climb,
- rocking or swaying,
- head-down fatigue,
- hands-off / adjusting,
- not on bike.

The S22i camera is likely good enough for coarse labels if the rider is framed well and lighting is reasonable.

Relevant references:

- <https://huggingface.co/tasks/video-classification>
- <https://huggingface.co/models?other=action-recognition>
- <https://github.com/dronefreak/human-action-classification>

### Facial Behavior And Attention Models

Public “facial recognition” models are usually the wrong target if they identify who a person is. The useful family is facial behavior analysis: attention, drowsiness, expression, face landmarks, eye openness, mouth opening, and head pose.

These are adjacent to automotive driver-monitoring systems. Production automotive systems are usually more robust because they may use infrared cameras, fixed camera geometry, temporal smoothing, proprietary datasets, and safety validation. Public models and toolkits can still provide useful feature extraction.

Potentially useful signals:

- head drop;
- face visibility;
- gaze direction;
- eye openness or blink patterns;
- mouth opening;
- facial strain;
- facial landmark stability;
- face occlusion.

These should not be treated as identity recognition or literal emotion detection. They are weak behavioral features to combine with telemetry and heart-rate context.

Relevant references:

- <https://github.com/TadasBaltrusaitis/OpenFace>
- <https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker>
- <https://huggingface.co/ckcl/driver-drowsiness-detector>
- <https://huggingface.co/mosesb/drowsiness-detection-mobileViT-v2>

### Time-Series Physiological Modeling

This is likely the most promising category. Public examples of heart-rate prediction use lagged, rolling, and cumulative workout features because heart rate responds with delay.

For S22i rides, a personalized model could combine:

- cadence,
- resistance,
- watts,
- incline,
- heart rate,
- interval phase,
- breathing intensity,
- posture/motion features,
- seated/standing state.

Possible outputs:

- estimated RPE,
- fatigue onset,
- recovery quality,
- HR drift anomaly,
- “today is harder than usual” detection,
- predicted ability to complete the next interval.

Relevant reference:

- <https://huggingface.co/rricc22/heart-rate-prediction-lstm>

## Recommended Research Direction

Do not start by recording every ride as raw video and audio. That creates a large privacy-heavy archive before knowing which signals matter.

Instead, start with a feature-first dataset:

- Sample once per second.
- Store ride telemetry: cadence, watts, resistance, incline, speed, heart rate if available.
- Store derived audio features: RMS, peak, clipping, spectral bands, breathing/noise classifier output.
- Store derived vision features: rider present, seated/standing, head/torso position, motion/sway score.
- Store explicit event labels: interval start/end, manual “mark,” cadence collapse, HR drift spike, high breathing intensity.
- Optionally save short raw snippets only around marked or interesting events.

This keeps the experiment more scientific and privacy-respecting while still allowing later ML work.

## Most Promising Product Hypothesis

The strongest candidate is a **private third-person ride execution review**:

- Input: planned workout, Zwift route, S22i telemetry, Garmin activity data, and weak media-derived features.
- Output: a post-ride explanation of expected vs observed strain, where execution degraded, where expected effort was missing, and whether the plan/route pairing made sense.
- First value: explain why the ride felt the way it did.
- Later value: personalize fatigue/RPE models and suggest better route/workout pairings.

This is more compelling than generic posture coaching because it may add something Garmin alone does not know: the exact machine workload plus how the rider visibly and audibly handled that workload.

## Practical First Experiment

Build a passive ride logger that records derived features only:

- no raw video by default,
- no raw audio by default,
- one-second feature rows,
- optional short raw snippets around explicit rider marks,
- post-ride subjective labels such as “easy,” “sustainable,” “hard,” “failed,” or “felt wrong.”

After a few rides, inspect whether media-derived features correlate with meaningful events better than telemetry and heart rate alone. If they do not, the camera/mic path should remain a research curiosity rather than product scope.

The falsifiable question is:

> Do camera and microphone features explain ride quality, fatigue, or workout execution better than Garmin heart rate plus S22i watts, cadence, resistance, and incline alone?

If the answer is yes, the media path has product value. If the answer is no, the better product is probably route/workout/telemetry analysis without media.

A second falsifiable question is:

> Can the system detect both unexpected strain and missing expected strain in a way that changes the rider’s next workout or route choice?

This matters because under-execution is as useful to detect as overstrain. A ride can fail by being too hard, but it can also fail by not delivering the intended training stimulus.
