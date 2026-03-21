import os
import time
import json
from faster_whisper import WhisperModel
from pydub import AudioSegment

# -------------------------------------------------
# CONFIG
# -------------------------------------------------

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
audio_path = os.path.join(BASE_DIR, "data", "uploads", "8227c911-5b93-4977-a341-e7ff36c8ad19_agora_call_1771318707683.wav")
output_path = os.path.join(BASE_DIR, "data", "transcripts", "raw_transcript.json")

print("Audio Path:", audio_path)
print("Output Path:", output_path)

if not os.path.exists(audio_path):
    raise FileNotFoundError("Audio file not found!")

# -------------------------------------------------
# LOAD MODEL
# -------------------------------------------------

print("\n🔄 Loading MEDIUM model...\n")

model = WhisperModel(
    "medium",
    device="cpu",
    compute_type="int8"
)

print("✅ Model loaded successfully\n")

# -------------------------------------------------
# NORMALIZE AUDIO (IMPORTANT)
# -------------------------------------------------

print("🔧 Normalizing audio to 16kHz mono...\n")

audio = AudioSegment.from_file(audio_path)
audio = audio.set_frame_rate(16000)
audio = audio.set_channels(1)
audio = audio.normalize()

normalized_path = audio_path.replace(".mp3", "_normalized.wav")
audio.export(normalized_path, format="wav")

# -------------------------------------------------
# TRANSCRIBE
# -------------------------------------------------

print("🚀 Starting transcription...\n")

start_time = time.time()

segments, info = model.transcribe(
    normalized_path,
    language="en",
    beam_size=5,
    vad_filter=True,
    vad_parameters=dict(min_silence_duration_ms=500)
)

transcript = []

for segment in segments:
    transcript.append({
        "start": round(segment.start, 2),
        "end": round(segment.end, 2),
        "text": segment.text.strip()
    })

end_time = time.time()

# -------------------------------------------------
# SAVE OUTPUT
# -------------------------------------------------

os.makedirs(os.path.dirname(output_path), exist_ok=True)

with open(output_path, "w") as f:
    json.dump(transcript, f, indent=2)

# -------------------------------------------------
# PRINT RESULTS
# -------------------------------------------------

print("\n✅ Transcription completed")
print(f"⏱ Processing time: {round(end_time - start_time, 2)} seconds\n")

print("--------------- RAW TRANSCRIPT ---------------\n")

for seg in transcript:
    print(f"[{seg['start']}s - {seg['end']}s] {seg['text']}")

print("\nTranscript saved to:", output_path)
