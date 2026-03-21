import os
from dotenv import load_dotenv
from pydub import AudioSegment

# Point to local ffmpeg if bundled
AudioSegment.converter = os.path.join(os.getcwd(), "ffmpeg", "bin", "ffmpeg.exe")

# Load .env file from backend directory
load_dotenv(os.path.join(os.path.dirname(__file__), ".env"))

from fastapi import FastAPI
from backend.api.routes import router

app = FastAPI(
    title="AI Debate Analyzer",
    description="0-budget debate analysis system",
    version="1.0"
)

app.include_router(router)

@app.get("/")
def home():
    return {"status": "AI Debate Analyzer running"}

#use below command after activating venv to run the server
#uvicorn backend.main:app --host 0.0.0.0 --port 8000 --reload
