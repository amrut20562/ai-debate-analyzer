# AI Debate Analyzer - Setup & Run Guide

## 📦 PART 1 — UPLOADING PROJECT TO GITHUB
1. Install Git from [git-scm.com](https://git-scm.com/downloads).
2. Create a GitHub account and click **New repository** named `ai-debate-analyzer`. Do NOT add any setup files (README or gitignore) on GitHub yet.
3. Open PowerShell and navigate to your project:
   ```powershell
   cd c:\SYSTEM\SYSTEM2\Projects\ai_debate_analyzer
   ```
4. Run these exact commands to push cleanly:
   ```powershell
   git init
   git add .
   git commit -m "Initial working project codebase"
   git remote add origin https://github.com/YourUsername/ai-debate-analyzer.git
   git branch -M main
   git push -u origin main
   ```

---

## 📦 PART 2 — SETTING UP PROJECT ON ANOTHER PC
### Step 1: Install Software
- **Python 3.10+**: Must check **"Add Python.exe to PATH"**.
- **Git**, **Android Studio**
- **Ollama**: Need this for local AI.
- **FFmpeg**: Download Windows build from gyan.dev, extract to `C:\ffmpeg`, add `C:\ffmpeg\bin` to PATH.

### Step 2: Clone Code
```powershell
git clone https://github.com/YourUsername/ai-debate-analyzer.git
cd ai-debate-analyzer
```

### Step 3: Backend Setup
```powershell
cd backend
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```
**Keys & Configs:**
- Get `serviceAccountKey.json` and put it inside `backend/`.
- Rename `.env.example` to `.env` and fill `HF_AUTH_TOKEN=your_token`
- Run Server: `uvicorn main:app --host 0.0.0.0 --port 8000 --reload`

### Step 4: AI Model Setup
In a new PowerShell window:
```powershell
ollama pull mistral
# OR
ollama pull phi3:mini
```

### Step 5: Android App Setup
- Open Android Studio -> Open folder `Debate_v2`.
- Wait for Gradle to sync automatically.
- Paste `google-services.json` inside `Debate_v2/app/`.
- Run on Emulator or USB Device.

## ⚠️ TROUBLESHOOTING
- **No module named 'debate_analysis':** Run `.\.venv\Scripts\activate` before starting the server.
- **Memory Errors (mkl_malloc):** Switch AI Judge code to use `phi3:mini` instead of `mistral` to use less RAM.
- **Port 8000 in use:** Stop old servers or use `--port 8001`.
- **Firebase Permission Denied:** Ensure `serviceAccountKey.json` hasn't expired.
- **Cannot find PyDub / FFmpeg:** Ensure FFmpeg is configured in Windows PATH.
