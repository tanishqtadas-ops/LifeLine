# ❤️ LifeLine

## Offline CPR Emergency Companion

> **Sense. Understand. Guide.**

LifeLine is an offline, phone-first emergency companion designed to support bystanders during critical situations.

It combines:

- **Real-time CPR rhythm feedback** using the smartphone's accelerometer
- **Offline voice-based emergency guidance**
- **Semantic retrieval from a verified local protocol set**
- **Confidence-based safe fallback instead of generated medical advice**

LifeLine is designed as a **companion, not a replacement for emergency professionals or medical care**.

---

# 🚨 The Problem

During an emergency, a bystander may know that CPR or first aid is needed but still struggle with:

- Maintaining the correct CPR rhythm under stress
- Knowing what to do when an unexpected situation occurs
- Finding reliable information quickly
- Working without an internet connection
- Using a phone while both hands are occupied
- Knowing whether an AI-generated answer can actually be trusted

Traditional tools usually solve only one part of the problem.

A tutorial can explain CPR, but it does not monitor the rescuer's rhythm.

A metronome can provide a rhythm, but it does not know what the bystander is asking.

A web search provides information, but it depends on connectivity and requires searching.

A generic AI chatbot can answer questions, but generated medical advice can be unsafe.

**LifeLine combines these capabilities into one offline-first system.**

---

# 💡 Our Solution

LifeLine is built around three simple ideas:

### Sense

The phone's accelerometer captures repeated hand movement during CPR and estimates the compression rhythm in real time.

### Understand

The user can ask an emergency-related question using voice. Speech is processed locally on the device.

### Guide

The resulting query is matched against a locally stored set of verified emergency protocols.

LifeLine does not rely on a cloud LLM to generate medical advice.

---

# 🧠 How LifeLine Works

## CPR Monitoring

```text
Phone Movement
      ↓
Accelerometer
      ↓
Signal Processing
      ↓
Noise Reduction
      ↓
Peak Detection
      ↓
Compression Count
      ↓
Estimated BPM
      ↓
Live Rhythm Feedback
```
##The CPR monitor provides:

Compression count
Estimated compressions per minute
Session duration
Live rhythm classification

The prototype uses:
```
Below 100/min     → TOO SLOW
100–120/min       → GOOD RHYTHM
Above 120/min     → TOO FAST
```
The displayed values are calculated from real accelerometer data, not simulated numbers.


# 🎙️ Offline Emergency Guidance

LifeLine's voice-guidance architecture is designed to operate without internet access.

```
User Voice
    ↓
Offline Speech-to-Text
    ↓
Text Representation
    ↓
Semantic Retrieval
    ↓
Local Verified Protocols
    ↓
Confidence Check
    ↓
Verified Guidance
    OR
Safe Fallback

```

The system is designed so that the AI layer retrieves existing verified guidance instead of generating medical advice from scratch.


# 🛡️ Confidence-Based Safety

One of the core design principles of LifeLine is:

When the system does not know, it should not guess.

For every query, the system checks whether it matches a supported protocol with sufficient confidence.

High-confidence match

```
User Question
      ↓
Reliable Protocol Match
      ↓
VERIFIED
      ↓
Show corresponding guidance
```
Low-confidence match
```
User Question
      ↓
Weak / Unsupported Match
      ↓
UNKNOWN
      ↓
Do not invent an answer
      ↓
Direct the user toward professional emergency assistance
```
This prevents the system from behaving like an unrestricted generative chatbot during a high-risk situation.


# 📱 Main Features
CPR Monitor
Real accelerometer input
Real-time compression detection
Compression count
Estimated CPR rate
TOO SLOW / GOOD RHYTHM / TOO FAST feedback
Session timer
Start / Stop CPR training
Session summary
Ask LifeLine
Voice-oriented emergency assistance
Offline processing architecture
Semantic protocol matching
Verified guidance output
Confidence-aware responses
Safe UNKNOWN fallback
Offline-First

The core guidance workflow is designed to remain functional without an internet connection.

Privacy-Oriented Design

LifeLine does not require continuous cloud processing or a cloud-based conversational AI service for its core workflow.


# 🏗️ System Architecture
```
                         LIFELINE
                            │
              ┌─────────────┴─────────────┐
              │                           │
              ▼                           ▼
        CPR MONITOR                 ASK LIFELINE
              │                           │
              ▼                           ▼
       Accelerometer                  Microphone
              │                           │
              ▼                           ▼
      Motion Processing             Offline STT
              │                           │
              ▼                           ▼
       Peak Detection                Text Input
              │                           │
              ▼                           ▼
      Compression Rate              Embedding
              │                           │
              ▼                           ▼
       Live Feedback              Semantic Search
                                          │
                                          ▼
                                 Local Protocol Set
                                          │
                                          ▼
                                  Confidence Check
                                    │          │
                                  HIGH         LOW
                                    │          │
                                    ▼          ▼
                                VERIFIED    UNKNOWN
                                GUIDANCE    FALLBACK
```
All core processing is designed around on-device / offline operation.


# 🧪 Phase 1 Prototype

The Phase 1 prototype focuses on two core capabilities:

1. CPR Rhythm Monitoring

The prototype reads real smartphone accelerometer data and converts repeated movement into an estimated compression rate.

2. Offline Guidance Architecture

The prototype provides the Android-side interface and offline AI architecture required to receive and display verified protocol results.

The system is intentionally limited in scope so that the core workflow can be tested and demonstrated reliably.


# 🔬 Validation

LifeLine's CPR monitoring has been tested using a physical Android smartphone.

The prototype was tested with:

Real accelerometer movement
Different movement rates
Session start / stop behavior
Session reset behavior
CPR session summaries

Example prototype observations included:

```
~99 BPM   → TOO SLOW
~110 BPM  → GOOD RHYTHM
~122 BPM  → TOO FAST
```
The purpose of these measurements is prototype and training feedback, not medical-grade measurement.


# 🛠️ Technology Stack
| Component              | Technology                                 |
| ---------------------- | ------------------------------------------ |
| Platform               | Android                                    |
| Language               | Kotlin                                     |
| UI                     | Jetpack Compose                            |
| CPR sensing            | Android SensorManager + Accelerometer      |
| CPR processing         | On-device signal processing                |
| Speech recognition     | Offline speech-to-text                     |
| Semantic understanding | On-device text embeddings                  |
| Retrieval              | Semantic similarity matching               |
| Guidance               | Local verified protocol set                |
| Connectivity           | Not required for the core offline workflow |



# 🔒 Safety Boundaries

LifeLine is designed as an emergency companion and training/prototype system.

It does not:

Diagnose medical conditions
Measure or claim medical-grade blood pressure
Replace emergency services
Replace a doctor or trained emergency professional
Generate unrestricted medical advice
Claim medical-grade CPR measurement accuracy

When the system cannot confidently match a question to a supported protocol, it should abstain rather than guess.

In a real emergency, users should contact local emergency services and follow professional guidance.


# 🎯 Why LifeLine Is Different

Existing emergency tools often solve individual problems.

LifeLine brings several important capabilities together:
```
Real Phone Sensors
        +
Offline Intelligence
        +
Verified Protocol Retrieval
        +
Confidence-Based Abstention
```
The result is a phone-first, offline emergency companion designed around the needs of the bystander.

Recent apps remember where you were.
LifeLine helps you know what to do next.


# 🚀 Future Scope

The Phase 1 prototype is intentionally focused.

Future development could include:

Additional emergency scenarios such as choking
Camera-based supportive visual cues
Smarter session summaries
Additional sensor integration
Wearable / smart-device integration
Larger verified protocol coverage
Improved emergency-session handoff and visualization

These features would be added only after the core offline workflow is validated.


# 👥 Team : The Solver

Vinayak Tambole

Tanishq Tadas

Gouri Mundada

iQOO Hackathon 2026


# ❤️ Core Philosophy

LifeLine is built around four principles:

PHONE-FIRST
Use the device already in the bystander's hand.

OFFLINE-FIRST
Critical guidance should not depend entirely on connectivity.

SAFE
Avoid unsupported or unsafe claims.

GROUNDED
Retrieve verified guidance instead of inventing answers.

When every second matters, uncertainty should not become another emergency.
