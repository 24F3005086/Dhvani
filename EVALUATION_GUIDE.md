# 🎙️ Dhvani Voice Agent: Comprehensive Testing & Evaluation Guide

This testing guide provides a step-by-step methodology to evaluate **Dhvani** against the three official evaluation parameters:
1. **Latency** (End-to-End response speed)
2. **Transcription Accuracy** (Bilingual speech-to-text fidelity)
3. **Pronunciation Quality** (Acoustic clarity and naturalness, especially in Hinglish)

---

## 📊 The 3 Evaluation Parameters at a Glance

| Parameter | What Evaluators Look For | Dhvani Feature / Component | Target Benchmark |
| :--- | :--- | :--- | :--- |
| **1. Latency** | Time elapsed from the exact moment user stops speaking to the first audible syllable from the speaker | On-Device Silero VAD (200ms) + Groq Whisper STT (~300ms) + Groq LPU LLM (~150ms) + On-Device TTS | **English:** < 800 ms<br>**Hindi/Hinglish:** 1.5s - 3.2s |
| **2. Transcription Accuracy** | Zero word drops, proper handling of Indian accents, Devanagari/Romanized Hindi fidelity, correct numbers | Whisper Large V3 via Groq Cloud + Heuristic Language Identifier (LID) | **> 95% Word Accuracy** |
| **3. Pronunciation Quality** | Warm, articulate speech with natural pitch, correct syllables, and flawless number pronunciation in Hinglish | Supertonic 3 (Flow-Matching Diffusion) + Piper VITS + NumberNormalizer | **Human-grade clarity, 0 slurring** |

---

## 🛠️ Step-by-Step Testing Procedure

### Step 1: Prepare the Device & Environment
1. Ensure the phone (**OnePlus Nord CE4 Lite 5G**) is connected to steady Wi-Fi or 5G mobile data (required for Groq Cloud STT & LLM streaming).
2. Set media volume to **70%–80%** so audio is loud and distinct.
3. Open **Dhvani**. Verify:
   - Status bar reads: `Ready: Speak or tap a test phrase above`.
   - The Real-Time Pipeline Telemetry HUD is visible at the top. If collapsed, tap the **Speedometer icon** (top right) to open it.

### Step 2: Understand the HUD Metrics
When you speak, watch the HUD cards update in real time:
- **VAD (ms)**: Time taken to confirm speech end after you pause (set to ~200ms).
- **STT (ms)**: Time taken to transcribe your audio via Whisper Large V3 (~250–350ms).
- **LLM TTFT (ms)**: Time To First Token streamed from Groq (~100–180ms).
- **TTS TTFA (ms)**: Time taken by the on-device synthesis engine to generate the first audio block.
- **TOTAL E2E (ms)**: Total end-to-end response time.
- **LANGUAGE**: Auto-detected language (`English`, `Hinglish`, or `Hindi`).
- **DUPLEX**: `Active` (Echo Guard ensures Dhvani's own voice never interrupts itself).

---

## 🧪 Comprehensive Test Question Matrices

Execute these test suites covering all typical evaluator queries and edge cases.

### Suite 1: English Accuracy & Latency (Target: E2E < 850 ms)
> **Goal**: Evaluate rapid streaming latency and crisp Piper Amy pronunciation.

| # | Question to Speak | Expected Transcription | What to Verify in Response |
|---|---|---|---|
| **E1** | *"What is the weather forecast for Bangalore tomorrow?"* | `What is the weather forecast for Bangalore tomorrow?` | Responds in English within ~700ms; weather information stated clearly. |
| **E2** | *"Can you schedule a meeting with Dr. Rao at 4 PM on Friday?"* | `Can you schedule a meeting with Dr. Rao at 4 PM on Friday?` | Accurately captures "Dr. Rao" and "4 PM"; responds in 1 concise sentence. |
| **E3** | *"What is 24 multiplied by 4?"* | `What is 24 multiplied by 4?` | Responds in **English** with correct answer (`96`); does not switch to Hindi. |
| **E4** | *"Where is the Computational Intelligence Lab located at IISc?"* | `Where is the Computational Intelligence Lab located at IISc?` | Recognizes "IISc" acronym accurately; provides campus location. |
| **E5** | *"Give me a one-sentence tip for healthy sleep."* | `Give me a one-sentence tip for healthy sleep.` | Concise single-sentence answer; fluent English prosody. |

---

### Suite 2: Hinglish Everyday Queries (Target: Pronunciation Quality & Code-Switching)
> **Goal**: This is the primary parameter evaluators scrutinize. Verify that Dhvani recognizes mixed vocabulary and articulates Hindi words naturally with correct numbers.

| # | Question to Speak | Expected Transcription | What to Verify in Response |
|---|---|---|---|
| **H1** | *"Mera kal ka schedule kya hai aur meri next meeting kab hai?"* | `Mera kal ka schedule kya hai aur meri next meeting kab hai?` | Tagged as `HINGLISH`; answers in natural conversational Hinglish. |
| **H2** | *"Mujhe kal subah 7 baje ka alarm set karna hai."* | `Mujhe kal subah 7 baje ka alarm set karna hai.` | Number 7 is pronounced as *"सात"* (saat); voice is warm and crystal-clear. |
| **H3** | *"Bangalore campus mein nearest canteen kahan hai? Mujhe chai peeni hai."* | `Bangalore campus mein nearest canteen kahan hai? Mujhe chai peeni hai.` | Preserves both English words ("campus", "canteen") and Hindi words ("kahan hai", "chai peeni hai"). |
| **H4** | *"Aaj shaam ko traffic kaisa rahega Silk Board junction pe?"* | `Aaj shaam ko traffic kaisa rahega Silk Board junction pe?` | Recognizes Bangalore landmark "Silk Board"; fluent Hinglish response. |
| **H5** | *"Agar mere paas 500 rupaye hain aur 150 kharch ho gaye, toh kitne bache?"* | `Agar mere paas 500 rupaye hain aur 150 kharch ho gaye, toh kitne bache?` | Correct math (350); numbers pronounced naturally as Hindi words. |

---

### Suite 3: Pure Hindi (शुद्ध एवं स्वाभाविक हिंदी)
> **Goal**: Evaluate pure Devanagari/Hindi speech recognition and ensure Dhvani does not convert the query to English.

| # | Question to Speak | Expected Transcription | What to Verify in Response |
|---|---|---|---|
| **P1** | *"नमस्ते, आप मेरी किस प्रकार सहायता कर सकते हैं?"* | `नमस्ते, आप मेरी किस प्रकार सहायता कर सकते हैं?` | Responds in polite, natural Hindi; zero English substitution. |
| **P2** | *"भारत के प्रधानमंत्री कौन हैं और उनका जन्म कब हुआ था?"* | `भारत के प्रधानमंत्री कौन हैं...` | Devanagari transcription; factual and articulate answer. |
| **P3** | *"आज कौन सा वार है और आज की तारीख क्या है?"* | `आज कौन सा वार है और आज की तारीख क्या है?` | Date and day spoken clearly in Hindi without slurring. |
| **P4** | *"पच्चीस और पैंतीस का जोड़ कितना होता है?"* | `पच्चीस और पैंतीस का जोड़ कितना होता है?` | Correct answer (60 / साठ); authentic Hindi pronunciation. |

---

### Suite 4: Stress Tests & Duplex Barge-In
> **Goal**: Test system robustness, language boundary isolation, and interruption capability.

| Test Case | How to Test | Expected Behavior |
|---|---|---|
| **1. Barge-In Interruption** | While Dhvani is speaking a response, speak *"Hey Dhvani, stop"* or tap the central Orb. | Speech audio stops immediately (< 50ms); state changes back to `LISTENING` without feedback squeal. |
| **2. Echo Immunity** | Stand in a quiet room with speaker volume at 90%. Let Dhvani finish speaking. | Microphone does not pick up Dhvani's own voice as a new user message (Echo Guard protects against self-triggering). |
| **3. Fast Speaking** | Speak *"What time is it in Tokyo right now?"* rapidly. | Whisper Large V3 transcribes the sentence completely without dropped syllables. |
| **4. Speech Distance** | Speak from 1 meter away at normal conversational volume. | Silero VAD detects speech start and end reliably. |
| **5. Language Mode Override** | In the top selector, switch from `Auto Detect` to `English` or `Hinglish`. | Forces the assistant to reply strictly in the selected language regardless of input accents. |

---

## 📋 Evaluation Scoring Sheet

You can use this scoring table while running the evaluation:

```
================================================================================
DHVANI VOICE AGENT - EVALUATION RUNNER SCORECARD
Date: ______________  Evaluator: __________________  Device: OnePlus Nord CE4 Lite
================================================================================

1. LATENCY EVALUATION:
   [ ] English E2E Latency < 800ms                [Pass / Fail]  Actual: _____ ms
   [ ] Hindi/Hinglish E2E Latency Responsive      [Pass / Fail]  Actual: _____ ms
   [ ] LLM Time-To-First-Token < 200ms             [Pass / Fail]  Actual: _____ ms
   [ ] VAD Speech-End Detection ~200ms            [Pass / Fail]  Actual: _____ ms

2. TRANSCRIPTION ACCURACY:
   [ ] Pure English accuracy (> 95%)              [Pass / Fail]  Notes: _________
   [ ] Pure Hindi Devanagari accuracy (> 95%)     [Pass / Fail]  Notes: _________
   [ ] Mixed Hinglish vocabulary accuracy (> 95%) [Pass / Fail]  Notes: _________
   [ ] Proper Noun / Bangalore entity recognition [Pass / Fail]  Notes: _________
   [ ] Number recognition (digits / words)        [Pass / Fail]  Notes: _________

3. PRONUNCIATION QUALITY:
   [ ] English (Piper Amy): Crisp & clear         [Pass / Fail]  Rating: __ / 5
   [ ] Hindi/Hinglish (Supertonic 3): Natural     [Pass / Fail]  Rating: __ / 5
   [ ] No synthetic slurring or robotic artifacts [Pass / Fail]  Rating: __ / 5
   [ ] Numbers pronounced naturally in Hindi      [Pass / Fail]  Rating: __ / 5
   [ ] Zero unwanted filler words                 [Pass / Fail]  Rating: __ / 5

OVERALL VERDICT: [EXCELLENT / SATISFACTORY / NEEDS IMPROVEMENT]
================================================================================
```
