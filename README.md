<div align="center">
  <h1>🎙️ AI Debate Analyzer</h1>
  <p>A zero-budget, AI-powered system for recording, transcribing, analyzing, and scoring group discussions and debates.</p>
  
  [![FastAPI](https://img.shields.io/badge/Backend-FastAPI-009688?style=flat-square&logo=fastapi)](https://fastapi.tiangolo.com/)
  [![Android](https://img.shields.io/badge/Frontend-Android_Studio-3DDC84?style=flat-square&logo=android)](https://developer.android.com/studio)
  [![Ollama](https://img.shields.io/badge/AI_Model-Ollama-000000?style=flat-square&logo=ollama)](https://ollama.ai/)
  [![Whisper](https://img.shields.io/badge/Transcription-Faster_Whisper-blue?style=flat-square)](https://github.com/guillaumekln/faster-whisper)
</div>

---

## 📖 Overview

**AI Debate Analyzer** is a comprehensive software suite that brings the power of artificial intelligence to debate and group discussion analysis. Designed as a zero-budget project, it leverages state-of-the-art open-source models to process audio, transcribe speech, identify speakers, and evaluate arguments. 

The system consists of two main components:
1. **The Backend Engine:** A powerful FastAPI Python server that handles heavy lifting like speaker diarization, audio transcription, feature extraction, and LLM-based argument scoring.
2. **The Android App (`Debate_v2`):** A sleek frontend built in Android Studio, providing an intuitive interface for users to join debate sessions, record audio, and view real-time scoreboards and summaries.

## ✨ Key Features

- 🎧 **Speaker Diarization:** Automatically detects *who* is speaking and *when* using `pyannote.audio`.
- 📝 **Accurate Transcription:** Converts speech to text quickly and locally using `faster-whisper`.
- 🧠 **AI Debate Judge:** Utilizes local LLMs (like `Mistral` or `Phi-3` via Ollama) to evaluate arguments, extract features, and objectively determine a winner.
- 📱 **Mobile Frontend:** A user-friendly Android app for seamless session management.
- ☁️ **Cloud Synchronization:** Uses Firebase/Firestore to sync historical debate data, transcripts, and scoreboards.

## 🏗️ Architecture Stack

### Backend (`/backend`)
- **Framework:** FastAPI / Uvicorn
- **AI/ML Libraries:** PyTorch, Hugging Face Hub, Transformers
- **Audio Processing:** PyDub, FFmpeg
- **Transcription/Diarization:** Faster-Whisper, Pyannote.audio
- **Database:** Firebase Admin SDK (Firestore)

### Frontend (`/Debate_v2`)
- **Platform:** Android (Java/Kotlin)
- **Real-Time Comm:** Agora Token Server integration
- **Database:** Firebase Cloud Firestore

---

## 🚀 Setup & Installation Guide

### Prerequisites
Before you begin, ensure you have the following installed on your PC:
- **Python 3.10+** (Important: Ensure you check **"Add Python to PATH"** during installation)
- **Git**
- **Android Studio**
- **Ollama** (Required for the local AI Judge module)
- **FFmpeg**: 
  - Download the Windows build from [gyan.dev](https://www.gyan.dev/ffmpeg/builds/)
  - Extract the contents to `C:\ffmpeg`
  - Add `C:\ffmpeg\bin` to your System's Environment Variables `PATH`.

### 1. Clone the Repository
```powershell
git clone https://github.com/YourUsername/ai-debate-analyzer.git
cd ai-debate-analyzer
```

### 2. Backend Setup
Navigate to the backend folder, create a virtual environment, and install all necessary dependencies.

```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\activate   # On Windows
pip install -r requirements.txt
```

**Configuration:**
1. Generate a `serviceAccountKey.json` from your Firebase Console and place it inside the `/backend/` directory.
2. Rename the `.env.example` file to `.env`.
3. Open `.env` and configure your `HF_AUTH_TOKEN` (Hugging Face token - required for Pyannote models).
4. change the ip address in MainActivity.java and AudioUploder.java.
5. change the google map api key in manifest.xml file.


**Run the Server:**
```powershell
# Make sure your virtual environment is still activated
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```
The API will be live at `http://localhost:8000`.

### 3. Local AI Setup
Open a new PowerShell/Terminal window and pull your desired LLM via Ollama. 
*(Note: Phi-3 is recommended for lower RAM usage.)*

```powershell
ollama pull mistral
# OR
ollama pull phi3:mini
```

### 4. Android App Setup
1. Open **Android Studio**.
2. Select **Open an existing project** and navigate to the `Debate_v2` folder.
3. Wait for the Gradle sync to complete automatically.
4. Download your `google-services.json` from Firebase and paste it inside the `Debate_v2/app/` directory.
5. Hit **Run** to launch the app on your USB-connected Android device or an Emulator.

---

## 🛠️ Troubleshooting

- **`No module named 'debate_analysis'`:** You forgot to activate your Python virtual environment. Run `.\.venv\Scripts\activate` before starting the Uvicorn server.
- **Memory/RAM Errors (`mkl_malloc`):** Your PC might be running out of RAM while loading the AI models. Modify the backend code to use `phi3:mini` instead of `mistral`, as it has a significantly smaller memory footprint.
- **Port 8000 in use:** Another application is using this port. Kill the old server process or start FastAPI on a different port using `--port 8001`.
- **Firebase Permission Denied:** Ensure your `serviceAccountKey.json` is present in the database directory and hasn't expired.
- **Cannot find PyDub / FFmpeg:** Ensure that you have added the `bin` folder of your FFmpeg installation to your Windows System `PATH`.

---

## 📄 License
This project is open-source. Please ensure compliance with the licenses of the underlying ML models and libraries used (e.g., Pyannote, Faster-Whisper, Llama/Mistral).
