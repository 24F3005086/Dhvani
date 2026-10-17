# ध्वनि (Dhvani) — Multilingual Real-Time Voice Assistant
### Computational Intelligence Lab | Department of Aerospace Engineering | IISc Bangalore

**Author:** Sameer Mishra (IIT Madras B.S. Data Science, 2nd Year)  
**Email:** sameermishra0912@gmail.com  
**GitHub:** [github.com/24F3005086/Dhvani](https://github.com/24F3005086/Dhvani)  
**Target Hardware:** OnePlus Nord CE4 Lite 5G (Qualcomm Snapdragon 695 5G, 8GB RAM, OxygenOS 14)  
**License:** Apache License 2.0 (see [LICENSE](LICENSE))

---

## 🎯 Project Overview & IISc Evaluation Alignment

**Dhvani (ध्वनि)** is an ultra-fast, trilingual real-time voice assistant Android application developed for the **IISc Bangalore Computational Intelligence Lab** selection evaluation.

The system addresses three core technical evaluation criteria:
1. **⚡ Conversational Latency (End-to-End):** **Sub-second pipeline latency (~991 ms)** from speech silence endpoint to first audible audio buffer (`vadSpeechEndTime` $\rightarrow$ `ttsFirstAudioTime`), with total end-to-end conversational turnaround averaging **1.35s – 1.78s** across varied network conditions.
2. **🎯 Transcription Accuracy:** Robust dual STT pipeline featuring **Whisper Large V3** with contextual prompt biasing for Indian geography and Hinglish code-switching, backed by an on-device **Whisper Tiny INT8 (sherpa-onnx)** offline fallback.
3. **🗣️ Pronunciation Quality:** Natural speech synthesis for English, Romanized Hinglish, and Devanagari Hindi using an on-device neural architecture:
   - **English (Piper Edge):** High-speed, natural on-device neural VITS synthesis (`en_US-amy-medium` via sherpa-onnx) with tuned prosodic cadence.
   - **Hindi / Hinglish (Supertonic 3 Hindi):** Modern on-device flow-matching diffusion synthesis (`sherpa-onnx-supertonic-3-tts-int8`) with automated Grapheme-to-Phoneme (G2P) loanword normalization (*distance* $\rightarrow$ *डिस्टेंस*, *kilometer* $\rightarrow$ *किलोमीटर*, *canteen* $\rightarrow$ *कैंटीन*), backed up by Google Neural and Piper Rohan fallbacks.
   - Inspired by the acoustic research of the Computational Intelligence Lab (including Prof. Prathosh A.P.'s Vāgdhenu project), optimized for low-latency edge Android deployment.

---

## 🏗️ System Architecture

![Dhvani Pipeline Flowchart](dhvani_flowchart.png)

```
[ User Speaks ]
      │
      ▼
┌────────────────────────────────────────────────────────┐
│  Hardware Audio Capture (Android OS)                   │
│  • AudioRecord (16 kHz, 16-bit PCM Mono)               │
│  • Hardware NoiseSuppressor & AcousticEchoCanceler     │
└──────────────────────────┬─────────────────────────────┘
                           │ (Float PCM buffer)
                           ▼
┌────────────────────────────────────────────────────────┐
│  Voice Activity Detection (VAD) Engine                 │
│  • Silero VAD (ONNX Runtime via sherpa-onnx)           │
│  • 500ms silence endpointing + speech onset detection  │
│  • Half-Duplex Echo Gate (mutes mic during playback)   │
└──────────────────────────┬─────────────────────────────┘
                           │ (Utterance audio buffer)
                           ▼
┌────────────────────────────────────────────────────────┐
│  Speech-to-Text (STT) Engine                           │
│  • Primary: Whisper Large V3 with Prompt Biasing       │
│  • Fallback: On-Device Whisper Tiny INT8 (sherpa-onnx) │
│  • Audio-Level Language Detection ("en" vs "hi")       │
└──────────────────────────┬─────────────────────────────┘
                           │ (Transcript + Whisper Language)
                           ▼
┌────────────────────────────────────────────────────────┐
│  Language Routing & Single-Script Policy               │
│  • Multi-signal: Whisper audio LID + script checks     │
│  • Single-script guarantee: Roman for Hinglish,        │
│    Devanagari for Hindi, Latin for English             │
│  • Automated Arabic/Urdu Unicode (0x0600-0x06FF) purge │
└──────────────────────────┬─────────────────────────────┘
                           │ (Routed Language Context)
                           ▼
┌────────────────────────────────────────────────────────┐
│  Cloud LLM Reasoning Layer (Groq LPU via SSE)          │
│  • Qwen 3.8 27B (`qwen/qwen3.8-27b`) via OkHttp SSE    │
│  • Real-time token streaming (~250-350ms TTFT)         │
│  • Strips <think> reasoning tokens from audio stream   │
└──────────────────────────┬─────────────────────────────┘
                           │ (Streaming Token Delta)
                           ▼
┌────────────────────────────────────────────────────────┐
│  Streaming Sentence Chunker                            │
│  • Breaks token stream at clause & punctuation bounds  │
│  • Delimiters: '.', '?', '!', '\n', '।' (Purna Viram)  │
└──────────────────────────┬─────────────────────────────┘
                           │ (Incremental Sentence Chunks)
                           ▼
┌────────────────────────────────────────────────────────┐
│  On-Device Speech Synthesis (TTS)                      │
│  • English: Piper Edge (`en_US-amy-medium` ONNX)       │
│  • Hindi/Hinglish: Supertonic 3 (Flow-Matching Diff.)  │
│  • G2P loanword mapping for Indian English vocabulary   │
└──────────────────────────┬─────────────────────────────┘
                           │ (PCM Audio Samples)
                           ▼
┌────────────────────────────────────────────────────────┐
│  AudioTrack Playback & Tactile Barge-In                │
│  • Low-latency streaming Float PCM playback            │
│  • 1-Tap Tactile Barge-in: Tapping orb cancels audio   │
│  • 150ms post-speech acoustic room cooldown            │
└────────────────────────────────────────────────────────┘
```

---

## ⚡ Live Telemetry Benchmarks (OnePlus Nord CE4 Lite 5G)

Measurements captured directly via the built-in telemetry logger across automated trials on physical hardware over Wi-Fi/5G:

| Pipeline Stage | Technology | Latency | Measurement Boundary |
|---|---|---|---|
| **VAD Endpointing** | Silero VAD (ONNX) | **~200 ms** | Minimum silence threshold required to trigger utterance end |
| **Speech-to-Text (STT)** | Groq Whisper Large V3 | **438 ms** | In-memory WAV transmission to raw transcript receipt |
| **LLM Reasoning (TTFT)** | Groq Qwen 3.8 27B | **255 ms** | HTTP SSE request to arrival of first response token |
| **Speech Synthesis (TTFA)**| Supertonic 3 / Piper | **266 ms** | Clause chunking to first synthesized PCM audio buffer |
| **PIPELINE E2E (TTFA)** | **Speech-End to First Audio** | **991 ms** | Monotonic delta: `vadSpeechEndTime` $\rightarrow$ `ttsFirstAudioTime` |

> **Note on Latency Timing Boundaries:**  
> The **991 ms** pipeline measurement represents the active computational latency after speech completion. Because the **Streaming Sentence Chunker** dispatches the first clause to the on-device TTS while Qwen 3.8 27B is still generating remaining tokens, LLM generation and speech synthesis overlap in parallel. Under varied real-world network and acoustic conditions, total conversational turnaround (including full silence confirmation and audio buffer ramp-up) ranges between **1.35s and 1.78s**.

---

## 🧪 Benchmark Test Sentences (English, Hinglish & Pure Hindi)

Evaluation test phrases verified on the OnePlus physical device:

| # | Test Sentence | Language | Agent Spoken Response | Engine / Voice Used |
|---|---|---|---|---|
| 1 | *"What is the weather forecast for Bangalore tomorrow?"* | English | *"Tomorrow's weather in Bangalore is expected to be pleasant, around 28 degrees Celsius."* | Piper Edge (`en_US-amy-medium`) |
| 2 | *"Can you set a reminder for my meeting at 5 PM?"* | English | *"Got it, your reminder for the 5 PM meeting is set."* | Piper Edge (`en_US-amy-medium`) |
| 3 | *"Mujhe kal subah 7 baje ka alarm set karna hai."* | Hinglish | *"Aapka alarm kal subah 7 baje ke liye set kar diya gaya hai."* | Supertonic 3 Hindi (`sherpa-onnx`) |
| 4 | *"Aaj ka schedule kya hai, aur meri next meeting kab hai?"* | Hinglish | *"Aapki agli meeting aaj shaam 5 baje scheduled hai."* | Supertonic 3 Hindi (`sherpa-onnx`) |
| 5 | *"कल बैंगलोर का मौसम कैसा रहेगा?"* | Pure Hindi | *"कल बैंगलोर में मौसम सुहावना रहेगा और तापमान लगभग अट्ठाईस डिग्री रहेगा।"* | Supertonic 3 Hindi (`sherpa-onnx`) |
| 6 | *"चेन्नई से बैंगलोर की दूरी या डिस्टेंस कितना है?"* | Pure Hindi | *"चेन्नई से बैंगलोर की दूरी लगभग तीन सौ पचास किलोमीटर है।"* | Supertonic 3 Hindi (`sherpa-onnx`) |

---

## ✨ Key Engineering Highlights

1. **Trilingual Language Classifier & Single-Script Policy:**
   - Evaluates Whisper's audio-level language signal alongside Hindi grammatical postpositions (`hai`, `ka`, `ki`, `mein`, `chahiye`) and loanword tables.
   - **Single-Script Guarantee:** Hinglish queries are displayed and answered purely in Roman/Latin script (how users naturally communicate on messaging apps), while pure Hindi queries remain strictly in Devanagari script.
   - Automated Unicode detection intercepts and purges accidental Arabic/Urdu script leakage (`0x0600-0x06FF`), re-transcribing into standard Devanagari.
2. **Dedicated Voice Personas & G2P Normalization:**
   - 🇬🇧 **English:** Synthesized with Piper Edge (`en_US-amy-medium` via sherpa-onnx) for crisp articulation and sub-300ms generation.
   - 🇮🇳 **Hinglish/Hindi:** Synthesized with Supertonic 3 Hindi (`sherpa-onnx-supertonic-3-tts-int8`) via modern flow-matching diffusion. A custom Grapheme-to-Phoneme (G2P) table maps common English loanwords (*distance*, *kilometer*, *canteen*, *meeting*) into Devanagari phonemes so the neural voice articulates them naturally.
3. **Half-Duplex Echo Prevention & 1-Tap Tactile Barge-In:**
   - Audio input is paused during speech playback, eliminating the loudspeaker-to-microphone feedback loop where the assistant interrupts itself.
   - Includes a 150ms acoustic room dissipation cooldown after audio finishes.
   - Users can immediately halt assistant speech at any moment by **tapping the center pulsing orb or action button (tactile barge-in)**.
4. **Interactive 1-Tap Evaluation Chips:**
   - The UI includes clickable chips for standard benchmark queries (*Bangalore weather*, *Chennai distance*, *Next meeting*, *Hindi weather*), allowing instant, repeatable evaluation during demonstrations.
   - Every chip execution runs the full dynamic pipeline (real streaming LLM + real on-device neural TTS).

---

## 🛠️ Build and Setup Instructions

### Prerequisites
- Android Studio Ladybug / Iguana or newer
- Android SDK 35 (compileSdk 36, minSdk 26)
- Connected Android device (`arm64-v8a`) with USB Debugging enabled

### Quick Start
1. Clone the repository:
   ```bash
   git clone https://github.com/24F3005086/Dhvani.git
   cd Dhvani
   ```
2. Configure your Groq API key in `local.properties`:
   ```properties
   GROQ_API_KEY=gsk_your_key_here
   ```
3. Build and install the APK to your device:
   ```bash
   ./gradlew installDebug
   ```

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.  
Developed as part of the technical evaluation for the **Computational Intelligence Lab, Department of Aerospace Engineering, Indian Institute of Science (IISc), Bangalore**.
