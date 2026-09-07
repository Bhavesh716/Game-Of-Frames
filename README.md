<div align="center">
<img src="https://capsule-render.vercel.app/api?type=waving&color=0:0F172A,100:6D28D9&height=250&section=header&text=GAME%20OF%20FRAMES&fontSize=48&fontColor=ffffff&animation=fadeIn&fontAlignY=38&desc=Every%20character%20has%20its%20throne.&descAlignY=58&descAlign=50" width="100%"/>

<p align="center">
<b>Create beautiful organized shareable image collages from portrait videos with just One click of a button!</b>
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

<br>
<br>

</p>

<p align="center">

![Status](https://img.shields.io/badge/Status-Submission%20Ready-4F46E5?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?style=for-the-badge&logo=kotlin)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose)
![ML Kit](https://img.shields.io/badge/ML%20Kit-Face%20Detection-EA4335?style=for-the-badge&logo=google)
![TFLite](https://img.shields.io/badge/TensorFlow%20Lite-MobileFaceNet-FF6F00?style=for-the-badge&logo=tensorflow)
![On-Device](https://img.shields.io/badge/AI-100%25%20On--Device-16A34A?style=for-the-badge)
![minSdk](https://img.shields.io/badge/minSdk-26-02569B?style=for-the-badge&logo=android)

</p>

</div>

---

## 🎬 The Problem

Finding out "who's actually in this video, and how many times did each person show up" is tedious to do by hand.

You'd have to:

- Scrub through the whole clip
- Pause on every new face
- Screenshot it
- Crop it by hand
- Try to remember who you already counted
- Repeat, for every person, every time they reappear

For a 3-second clip, that's trivial.

For a 30-second clip with five people drifting in and out of frame, sharing the screen in pairs, at odd angles, partially clipped at the edge — it stops being trivial, and a human doing it by eye starts making mistakes.

Most naive "auto-collage" tools get this wrong in one of two ways:

- They lump different people together, or split one person into several, because they have no real notion of identity.
- They crop tightly to the detected face bounding box, which produces small, low-resolution, passport-photo-looking tiles instead of something you'd actually want to share.

A version of this that actually works has to answer, correctly, on **any** portrait video, without being told anything about who's in it:

- How many distinct people actually appear?
- How many times does each of them appear, and for how long?
- Which single frame best represents each of them?
- How do you build one clean photo *per person* out of a source frame that might contain two or three people at once — without one person's face bleeding into another's tile?

Game Of Frames is built to answer all four, entirely on-device, for a video it has never seen before.

---

## 💡 Our Philosophy

Making this collage by hand :-

```
Watch the full video
↓
Pause on every new face
↓
Screenshot
↓
Crop by hand
↓
Lose count of who you've already seen
↓
Arrange everything in a design app
↓
Repeat for every person, every appearance
```

Game Of Frames :-

```
Pick a video
↓
Detect every face, in every sampled frame
↓
Recognize the same person across the whole clip
↓
Track exactly when each person is on screen
↓
Score every sighting for quality
↓
Crop generously, one person at a time
↓
One collage. Every person, once.
```

Nothing here is hardcoded to a specific video, and nothing runs off-device.


---

## 🚀 What is Game Of Frames?

Game Of Frames is an on-device Android app that turns any portrait video into a presentable, shareable collage — one photo per unique person who appears in it, generated automatically.

Point it at a video. It can:

- Detect every face in every sampled frame — never assumes one person per frame
- Recognize the same person across the whole video using real on-device face embeddings — never a guess, never a filename
- Track exactly when each person is visible, frame by frame, so two people sharing a frame each get their own appearance, correctly
- Score every sighting of every person for frontality, sharpness, open eyes and expression, and keep only the best one
- Build a generous, person-specific crop around each face — never a tight box, never someone else's face leaking into frame, even when several people share the exact same source frame
- Let the user pinch-zoom, pan and swap the photo inside any collage tile before saving
- Save the finished collage to the gallery and share it through the standard Android share sheet

But Game Of Frames does not try to be clever about *who* someone is beyond recognizing they're the same face twice — it counts and frames people, it doesn't identify or label them.

---

## 🧠 Identity Intelligence

Deciding "is this the same person I already saw" is the actual hard problem here, so it runs on one deterministic rule instead of a black box:

```
Face
↓
Embed (MobileFaceNet, 112×112, fully on-device)
↓
Compare against EVERY known person's reference bank
↓
Highest similarity wins
↓
Above threshold  → same person
Below threshold  → new person, IF the face is trustworthy enough (see below)
```

- **One centralized similarity threshold** — never scattered across per-frame heuristics.
- **A reference bank, not one blurred-together average.** Each person keeps up to five real embeddings and is matched against whichever fits best, so one bad angle can't drag the whole identity off course.
- **ML Kit tracking is never used as identity.** It is not even requested from the detector — global identity comes from the embedding, full stop.
- **No clustering pass. No post-hoc reconciliation pass. No silent merges.**

A poor-quality face (small, edge-clipped, blurry) can still be *matched* to someone already known — continuity should never require a perfect look every time — but it can never single-handedly create a brand-new identity. And a person who is only ever seen at poor quality (a side profile drifting at the edge of frame, say) isn't simply discarded either: their evidence quietly accumulates across frames, and only once several independent, mutually-consistent sightings agree does the app confirm them as a real person — checked once more against everyone already known first, so the same evidence can never create a duplicate.

---

## 🧩 The Embedding Model

**Model:** MobileFaceNet, trained by [`sirius-ai/MobileFaceNet_TF`](https://github.com/sirius-ai/MobileFaceNet_TF) — a TensorFlow implementation of *MobileFaceNets: Efficient CNNs for Accurate Real-Time Face Verification on Mobile Devices* (Chen et al., arXiv:1804.07573). Apache License 2.0. Reported accuracy: 99.4%+ on LFW.

**File in this repo:** `app/src/main/assets/mobilefacenet.tflite`, obtained from the TFLite conversion mirrored at [`MCarlomagno/FaceRecognitionAuth`](https://github.com/MCarlomagno/FaceRecognitionAuth) (BSD-3-Clause), unmodified. Full provenance and license text: `app/src/main/assets/NOTICE_mobilefacenet.md`.

**Verified, not assumed** — inspected directly with the LiteRT Python interpreter before it was ever wired into the app:

| | Shape | Dtype |
|---|---|---|
| Input `input` | `[1, 112, 112, 3]` | `float32`, RGB, NHWC |
| Output `embeddings` | `[1, 192]` | `float32` |

No quantization — a plain float32 model, ~5.2 MB on disk, packaged uncompressed so it can be memory-mapped directly.

**Preprocessing & alignment** (`TfLiteFaceEmbedder`):

1. **Alignment.** When ML Kit resolves both eye landmarks, the crop is aligned with a proper similarity transform — rotation **and** scale **and** translation — mapping the subject's own eyes onto the standard published ArcFace/InsightFace 112×112 canonical eye positions (`Matrix.setPolyToPoly`). This is the same alignment convention MobileFaceNet-family models are trained on, and it fixes two things a naive crop gets wrong at once: an un-normalized scale, and a non-square crop that would otherwise get silently stretched to fit 112×112.
2. **Fallback.** If landmarks aren't available (a heavily turned or occluded face) or the implied rotation is implausibly large, a plain rectangular crop is used instead — squared before resizing, so it's still never a distorted, stretched-square input.
3. **Normalization.** Each channel is mapped as `(pixel − 128.0) / 128.0`, i.e. `[0, 255] → ≈[-1, 1)`.
4. **Output.** The raw 192-d embedding is L2-normalized before it's used for anything — cosine similarity between two embeddings is then just a plain dot product.

One `Interpreter` instance is created once per analysis run and reused for every face in the video, `close()`d in a `finally` block whether the run finishes normally, fails, or is cancelled.

---

## 🎯 The Similarity Threshold — How 0.52 Was Chosen

`IDENTITY_MATCH_THRESHOLD` is a single named constant (`GlobalIdentityMatcher`) — nothing else in the pipeline hardcodes a similarity number.

It was first calibrated empirically, not guessed — against real photos of real people, run through this exact model with this exact preprocessing: multiple different photos each of several public figures (sourced from Wikimedia Commons under permissive licenses), covering genuine across-session pose, lighting and camera variation:

| | Pairs | Min | Mean | Max |
|---|---|---|---|---|
| Within-person | 10 | 0.33 | 0.70 | 0.97 |
| Across-person | 24 | -0.06 | 0.27 | 0.55 |

That calibration placed the original threshold at **0.55** — the top of the across-person range and the low end of the within-person range, a deliberately conservative choice that favors never merging two different people over never splitting one person into two.

Once the pipeline's own on-device diagnostics showed clean, correctly-aligned same-person matches consistently landing at **0.97–0.99** with large margins — well clear of the across-person range above — the threshold was nudged down modestly, not aggressively, to **0.52**, trading a little of that conservative margin for slightly better recall on harder side-angle and partial-face sightings, without approaching the across-person ceiling the original calibration measured.

**Stated plainly:** this is still an evidence-based starting point, not a claim of a formally optimal value — a more rigorous pass would calibrate directly against a much larger, labeled set of the app's own on-device diagnostic runs. It is exactly one constant, specifically so it can be retuned later without touching any matching logic.

---

## ⏱️ Appearance Intelligence

An **appearance** is one continuous, clearly-visible segment — not a face count, not a frame count. The counting rule is exact, so the tracker matches it exactly, frame by frame:

```
currentFramePersons = who is visible THIS frame
lastFramePersons    = who was visible the frame before

new       = current − last        → starts a new appearance
ended     = last − current        → closes an appearance
continued = current ∩ last        → nothing changes
```


- Two people sharing one frame produce **one appearance each** — never one shared appearance.
- The same person detected twice in one frame (a detector double-hit) still counts **once**.
- A frame the app decides is too blurry to trust is skipped **entirely, before any of this runs** — it never reaches identity or appearance logic, so a blurry frame can never look like "this person just left."

**Worked example:** five distinct people, four appearances each, twenty appearances total. A and B share the frame from 10.1–11.5s. C and D share it from 20.2–21.6s. Both pairs still count as one appearance *per person*, not one shared appearance.

---

## 🖼️ Representative Frame Intelligence

Every sighting of every person is scored the moment it's detected — never in a second pass:

| Signal | What it measures |
|---|---|
| 🎯 Frontality | How front-facing the head pose is (yaw, pitch, roll) |
| 🔎 Sharpness | In focus, not motion-blurred (Laplacian variance) |
| 👁 Eyes open | Mean eye-open probability |
| 🙂 Expression | Smiling / pleasant expression, as a bonus, never a requirement |
| 📐 Visibility | Full face visible, penalized smoothly as a face nears the frame edge |

The highest-scoring sighting of each person, across their *entire* appearance history, becomes their representative shot — favoring frontal, crisp, eyes-open, pleasant frames, and avoiding clipped faces or closed eyes wherever a better option exists.

---

## ✂️ Multi-Person Crop Intelligence

A single source frame can contain two or three people at once. Game Of Frames never uses that shared frame as-is for anyone — every person gets their **own** crop, generated independently:

```
Source frame
↓
Target person's face box
↓
Expand generously (head, hair, shoulders, background)
↓
Check every OTHER face detected in that same frame
↓
If another face would show up inside the crop → shrink / reposition
↓
Render — this person, and only this person
```

The same source frame is allowed to produce two completely different final crops for two different people — one photo never becomes a tight, low-resolution face-box thumbnail, and one person's tile never shows a sliver of somebody else's face if a cleaner option exists.

---

## 🎨 The Collage Studio

The finished collage isn't a fixed render — every tile stays editable:

- 🤏 **Pinch-to-zoom / pinch-to-shrink** and **two-finger pan**, independently, on every tile — zooming around wherever you actually pinch, never snapping back to center.
- 🔁 **Swap** — tap a tile to reveal a small swap icon; pick any other photo of *that same person* and it drops straight into the slot with its own fresh zoom/pan state.
- 🖼 **Fullscreen viewer** — tap any photo in a person's album to open it full-screen, arbitrary-point zoom and pan, with save-to-gallery for the real high-resolution image (never the small recognition crop).
- What you edit is exactly what gets saved and shared — the on-screen preview and the exported bitmap apply the identical transform.

---

## 🧠 On-Device, Always

No backend. No cloud inference. No network call anywhere in the processing path.

Face detection, face embedding, identity matching and appearance tracking all run locally, in one chronological pass over the video, using a bundled TensorFlow Lite model — the app works exactly the same with the phone in airplane mode.

---

## ⚠️ Current Limitation & Future Improvement

Game Of Frames currently has a known limitation when processing frames containing **multiple faces simultaneously**. 

While the face detection and identity-matching pipeline works reliably for most single-face and typical multi-face scenarios, the on-device recognition model can occasionally produce inconsistent identity matches when several faces are present in the same frame. This can affect identity grouping and, consequently, appearance counts in a small number of cases.

This limitation is primarily a result of the **restricted submission timeline**. <br/>
Given the limited development window, I prioritized building and integrating the complete end-to-end pipeline — video processing, face detection, face embeddings, identity matching, appearance tracking, representative-frame selection, collage generation, saving, and sharing — rather than leaving the core product incomplete while pursuing a more extensive multi-face recognition refinement.

### 🔧 How I Would Improve It

With additional development time, I would address this by introducing a dedicated **multi-face branching stage** before identity matching.

Instead of passing a multi-face frame directly through the same recognition flow, the pipeline would:

```text
Multi-Face Frame
       ↓
Detect N Faces
       ↓
Create N Person-Specific Crops
       ↓
Slightly Expand / Zoom Out Each Crop
       ↓
Exclude Other Detected Faces Where Possible
       ↓
Process Each Crop Independently
       ↓
Face Alignment → Embedding → Identity Matching
       ↓
Store Each Result Independently
       ↓
Update Appearance Tracking
```

For example, if a frame contains two people:

```
Original Frame
┌─────────────────────────────┐
│       Person A   Person B   │
└─────────────────────────────┘
              ↓
       ┌───────────────┐
       │               │
       ▼               ▼
   Person A Crop   Person B Crop
       │               │
       ▼               ▼
   Embedding A     Embedding B
       │               │
       ▼               ▼
   Identity A      Identity B
```

Each person would therefore be treated as an independent single-face recognition input, while still retaining the original frame and timestamp for representative-shot reconstruction.

The crop would also be slightly more generous than the face bounding box so that the model receives enough facial context, while being carefully constrained to minimize pixels belonging to neighboring people.

This approach would preserve the existing identity-matching and appearance-tracking architecture while making multi-person frames much more robust and deterministic.

This is a known, bounded limitation rather than a hard architectural limitation. The core pipeline is already designed around independent face observations, so the proposed improvement would primarily strengthen the multi-face preprocessing and recognition stage.

---

## 🏗 Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Navigation Compose |
| Face Detection | ML Kit (`com.google.mlkit:face-detection`) |
| Face Embedding | TensorFlow Lite — MobileFaceNet, on-device |
| Identity Matching | Custom deterministic cosine-similarity matcher |
| Appearance Tracking | Custom set-difference temporal tracker |
| Video Decoding | `MediaMetadataRetriever` |
| Collage Export | Classic `android.graphics.Canvas` bitmap renderer |
| Gallery / Share | `MediaStore` + Android share sheet |
| Testing | JUnit (JVM unit tests, no device required) |
| minSdk | 26 |

---

## 🏗️ System Architecture

```
Video URI
  → VideoMetadataReader                (duration / resolution)
  → FrameExtractor                     (~10 fps deterministic sampling)
  → for every sampled frame:
       blur/quality gate               → unusable frames skipped before anything else
       MlKitFaceDetector.detect        → every face, left → right, independently
       for every face:
         HeuristicQualityScorer.score  → frontality / sharpness / eyes / expression
         TfLiteFaceEmbedder.embed      → 112×112 aligned crop → 192-d embedding
         GlobalIdentityMatcher.assign  → highest similarity wins, one threshold
         UnresolvedCandidateTracker    → accumulates poor-quality-but-consistent faces
       SetDiffAppearanceTracker.onFrame
  → SetDiffAppearanceTracker.finish()  → every person's appearance history
  → PersonCropSelector + generous, contamination-aware, person-specific crops
  → DefaultCollageGenerator.layout     → CollageLayout (pure geometry)
  → CollageCanvas (preview) / CanvasCollageExporter (save & share) — same layout, same crops
```

Core design principles:

- 🧩 **One interface, one implementation.** Every pipeline stage lives behind a small interface named after what it does — no abstraction kept "just in case."
- 🔁 **One chronological pass.** Detection, recognition and appearance tracking all happen together, frame by frame — never a second full decode of the video.
- 🚫 **No global clustering, no reconciliation, no tracking-ID-forced identity.** Every decision is made once, deterministically, from the recognition model's own evidence.
- 🖼️ **Preview equals export.** The exact same layout, crop and user-applied transform renders both the on-screen preview and the saved/shared bitmap.
- 🧯 **Never crash on bad input.** Every crop/coordinate calculation is defensive against zero-size, NaN, Infinity and inverted ranges — verified by dedicated unit tests.

---

## 📁 Project Structure

```
Game-Of-Frames/
│
├── app/
├── gradle/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
├── LICENSE.txt
└── README.md
```

---

## 🚀 Setup & Installation

Game Of Frames is a native Android application built with Kotlin and Gradle.

The repository includes the Gradle Wrapper, so you do **not** need to install Gradle separately.

### 📋 Prerequisites

Before running the project, make sure you have:

- [Android Studio](https://developer.android.com/studio) installed
- Android SDK installed through Android Studio
- Git installed
- An Android device or Android Emulator

> **Note:** Open the project using the Android Studio project root. Do not open the `app` folder directly.

---

## 📥 1. Clone the Repository

Open a terminal and run:

```bash
git clone https://github.com/Bhavesh716/Game-Of-Frames.git
```

Navigate into the project:

```bash
cd Game-Of-Frames
```

The project structure should look similar to:

```
Game-Of-Frames/
│
├── app/
├── gradle/
├── .gitignore
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
├── LICENSE.txt
└── README.md
```

---

## 🛠️ 2. Open in Android Studio

1) Open Android Studio
2) Select Open
3) Select the cloned Game-Of-Frames folder
4) Wait for Android Studio to load the project
5) Allow Gradle to sync and download the required dependencies

If Gradle Sync does not start automatically, use:

```
File → Sync Project with Gradle Files
```

Wait until the synchronization completes successfully.

---

▶️ 3. Run the Application

### Using an Android Emulator

- Open Device Manager in Android Studio
- Create or select an Android Virtual Device
- Start the emulator
- Select the emulator from the device selector
- Click Run ▶
  
### Using a Physical Android Device

- Enable Developer Options on your Android device
- Enable USB Debugging
- Connect the device to your computer using USB
- Accept the USB debugging authorization prompt on the device
- Select the connected device in Android Studio
- Click Run ▶

Android Studio will build and install the application automatically.

---

## 📦 4. Build the APK

The application can also be built directly from the terminal using the included Gradle Wrapper.

Windows

From the project root:

```
.\gradlew.bat assembleDebug
```

macOS / Linux

```
./gradlew assembleDebug
```

After a successful build, the APK will be available at:

```
app/build/outputs/apk/debug/app-debug.apk
```

You can then transfer this APK to an Android device and install it.

---

## 📲 5. Install APK Using ADB

If ADB is configured, the generated APK can be installed directly using:

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

If an older version of Game Of Frames is already installed:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🏗️ 6. Build APK Using Android Studio

The APK can also be generated without using the terminal.

In Android Studio:

```
Build
    → Generate App Bundles or APKs
    → Generate APKs
```

After the build completes, Android Studio will provide an option to locate the generated APK.

The debug APK will be generated under:

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎮 7. Using Game Of Frames

After launching the application:

- Select a portrait video from the device.
- Start the analysis.
- Wait while Game Of Frames processes the video.
- The application detects faces and groups appearances belonging to the same person.
- Review each detected person's best representative frame and appearance count.
- Tap Create Collage.
- Review the generated collage.
- Save the collage to the device gallery or share it using the standard Android share sheet.

---

## ✅ Assignment Requirements — What's Covered

| Requirement | Status |
|---|---|
| Process any similar portrait video, no hardcoding | ✅ Every number is computed fresh at runtime; sample clips aren't bundled or special-cased |
| Load video, show clear processing progress | ✅ Live stage, frame count, face count, people found, appearances found |
| Processing off the main thread | ✅ `Dispatchers.Default` / `Dispatchers.IO`, cold `Flow<ProcessingState>` |
| Face detection + face embeddings + clustering, all three required | ✅ ML Kit detection → MobileFaceNet embeddings → deterministic identity matching |
| Show each person's appearance count | ✅ Per-person appearance history on the Results and Person Detail screens |
| One collage per video, every person exactly once | ✅ `generateFullCollage()` — one tile per confirmed person |
| Representative shot judged on frontality, sharpness, eyes-open, expression | ✅ `HeuristicQualityScorer`, see "Representative Frame Intelligence" above |
| No tight face-bbox crops | ✅ `PersonCropSelector` — generous, proportional, contamination-aware |
| Good-looking, shareable, creative collage | ✅ Editorial hand-tuned layouts, zero text baked into the image, editable tiles |
| Save to gallery + standard Android share sheet | ✅ `MediaStore` + `Intent.ACTION_SEND` |
| Kotlin, minSdk 26 | ✅ |
| ML Kit for detection, on-device embedding model, documented | ✅ MobileFaceNet — see "Identity Intelligence" and `app/src/main/assets/NOTICE_mobilefacenet.md` |
| Everything on-device, no backend | ✅ No network permission is even declared |

---

## 🎯 Why It Matters

📸 **No manual scrubbing** — the tedious part of making this collage by hand disappears entirely.

🧑‍🤝‍🧑 **Correct on multi-person frames** — two or three people sharing a frame are never merged, split, or given each other's faces.

✂️ **Never a tight, ugly crop** — every tile looks like a photo someone would actually want to share.

📶 **Works with zero connectivity** — nothing here ever leaves the device.

🎨 **Still yours to finish** — zoom, pan and swap any tile before you save it.

---

## 🌎 Vision

We didn't want to build another "auto-crop" tool that quietly gets the count wrong and calls it a collage.

We wanted a small, deterministic, honestly-documented pipeline that answers a genuinely hard question — *who is in this video, and when* — correctly, on-device, on a video it has never seen before.

That's the bar Game Of Frames is trying to clear.

---

## 💎 Author

Built by **Bhavesh Gudlani** — internship assignment submission.

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:6D28D9,100:0F172A&height=150&section=footer" width="100%"/>
