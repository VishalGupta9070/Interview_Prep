#InterviewPrepAI

AI-powered Android interview preparation application built using Jetpack Compose, MVVM, Room Database, and Google Gemini AI APIs.

The app helps candidates prepare for interviews through:
Resume analysis
AI-generated interview questions
Structured mock interviews
Voice-based answering
Progressive interview rounds

✨ Features
📄 Resume Upload & Analysis
Upload resume PDF
Extract text using PDF parsing/OCR pipeline
Detect candidate profession automatically
Detect experience level and skills

🤖 AI-Based Question Generation
Generate interview preparation questions based on:

Resume content
Skills
Profession
Experience level
Structured Rounds

Questions are grouped into:

HR Round
Technical Round 1 (Easy)
Technical Round 2 (Medium)
Technical Round 3 (Hard)

🎤 AI Mock Interview
Interactive mock interview system where AI:

Asks questions one-by-one
Progressively increases difficulty
Adapts questions based on candidate profile
Covers:
HR questions
Technical basics
Intermediate concepts
Advanced/system design questions
Resume/project discussions
🎙 Speech-to-Text Support

Users can answer questions by:

Typing manually
Using voice input (Speech-to-Text)

Voice input appends text inside the answer field without breaking manual typing support.

💾 Room Database Integration

User-specific local persistence using Room Database.

Stores:

Resume details
Extracted profession
Generated interview questions
Generated answers

Supports:

Multi-user separation
Session-based data loading
Offline persistence

🔐 Authentication
Simple login system using:

10-digit phone number
Password
Session handling included.

🚪 Logout Support
Clears active session
Redirects to Log in screen
Prevents back navigation after logout

🏗️ Tech Stack
Android
Kotlin
Jetpack Compose
Navigation Compose
Material 3
Architecture
MVVM
Repository Pattern
StateFlow
Coroutines
Local Storage
Room Database
SharedPreferences / Session Management
Networking
Retrofit
OkHttp
AI Integration
Google Gemini API
Voice Features
Android SpeechRecognizer API

📱 Architecture
UI (Compose)
↓
ViewModel
↓
Repository
↓
Room DB + Gemini API
🧠 Dynamic UI Rendering

The app demonstrates:
Single LazyColumn
Multiple UI item types
Sealed classes
Dynamic composable rendering

Similar to:
Multiple View Types in RecyclerView
Single Adapter Architecture