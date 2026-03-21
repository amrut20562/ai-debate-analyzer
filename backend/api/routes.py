from fastapi import APIRouter, UploadFile, File, BackgroundTasks
import shutil
import os
import json
import uuid
import gc
from datetime import datetime
import firebase_admin
from firebase_admin import credentials, firestore
from fastapi import Body



router = APIRouter(prefix="/analyze", tags=["Debate Analysis"])

# =====================================================
# Directories
# =====================================================

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

UPLOAD_DIR = os.path.join(BASE_DIR, "data", "uploads")
TRANSCRIPT_DIR = os.path.join(BASE_DIR, "data", "transcripts")
JOBS_DIR = os.path.join(BASE_DIR, "data", "jobs")

os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(TRANSCRIPT_DIR, exist_ok=True)
os.makedirs(JOBS_DIR, exist_ok=True)

# =====================================================
# Background job worker
# =====================================================
def get_firestore():
    if not firebase_admin._apps:
        base_dir = os.path.dirname(
            os.path.dirname(os.path.abspath(__file__))
        )
        key_path = os.path.join(base_dir, "serviceAccountKey.json")

        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)

    return firestore.client()


def map_speakers_to_participants(transcript, participants):
    """
    Robust speaker → participant mapping using:
    - word count
    - turn count
    - speaking duration proxy
    """

    speaker_stats = {}

    for seg in transcript:
        speaker = seg["speaker"]
        text = seg["text"]

        if speaker not in speaker_stats:
            speaker_stats[speaker] = {
                "words": 0,
                "turns": 0,
                "duration": 0.0
            }

        words = len(text.split())
        speaker_stats[speaker]["words"] += words
        speaker_stats[speaker]["turns"] += 1

        # duration proxy: 0.4 sec per word (rough but stable)
        speaker_stats[speaker]["duration"] += words * 0.4

    # dominance score
    dominance = {
        s: (
            stats["words"] * 1.0 +
            stats["turns"] * 2.0 +
            stats["duration"] * 0.5
        )
        for s, stats in speaker_stats.items()
    }

    sorted_speakers = sorted(
        dominance.items(),
        key=lambda x: x[1],
        reverse=True
    )

    participants_sorted = sorted(
        participants,
        key=lambda p: p["joinedAt"]
    )

    speaker_map = {}
    for i, (speaker, _) in enumerate(sorted_speakers):
        if i < len(participants_sorted):
            speaker_map[speaker] = participants_sorted[i]

    return speaker_map



def run_debate_job(job_id: str, audio_path: str, metadata_path: str):
    job_file = os.path.join(JOBS_DIR, f"{job_id}.json")
    result_file = os.path.join(JOBS_DIR, f"{job_id}_result.json")

    try:
        # mark processing
        with open(job_file, "w") as f:
            json.dump({"status": "processing"}, f)

        with open(metadata_path) as f:
            meta = json.load(f)

        # lazy imports (heavy)
        from backend.audio_processing.diarization import run_diarization
        from backend.audio_processing.transcription import transcribe_with_diarization
        from backend.debate_analysis.feature_extraction import extract_features
        from backend.debate_analysis.scoring import score_speakers, decide_winner
        from backend.debate_analysis.summary import generate_summary
        
        print("🚀 Starting AI job:", job_id)

        diarization_path = os.path.join(
            TRANSCRIPT_DIR, f"{job_id}_diarization.json"
        )

        transcript_path = os.path.join(
            TRANSCRIPT_DIR, f"{job_id}_transcript.json"
        )

        num_speakers = len(meta.get("participants", []))
        diarization = run_diarization(
            audio_path, diarization_path,
            num_speakers=num_speakers if num_speakers >= 2 else None
        )
        print("✅ Diarization complete")


        transcript = transcribe_with_diarization(
            audio_path, diarization_path, transcript_path
        )
        print("✅ Transcription complete")

        features = extract_features(transcript)
        print("✅ Feature extraction complete")

        gc.collect()

        scores, ai_result = score_speakers(features, transcript)
        print("✅ Scoring complete")

        winner_speaker = decide_winner(scores, ai_result)
        print("🏆 Winner:", winner_speaker)


        # 🔥 MAP speakers → Firebase users
        speaker_map = map_speakers_to_participants(
            transcript,
            meta.get("participants", [])
        )

        winner_user = speaker_map.get(winner_speaker)

        winner_label = (winner_user["firebaseUid"] if winner_user else winner_speaker)


        summary = generate_summary(
            winner_label,
            scores,
            features,
            transcript,
            {k: v["firebaseUid"] for k, v in speaker_map.items()},
            ai_result
        )
        

        # ======================================
        # BUILD UI SCOREBOARD (CORRECT MAPPING)
        # ======================================

        scoreboard = []

        # 1️⃣ Add scored speakers
        for speaker, score in scores.items():
            user = speaker_map.get(speaker)
            if not user:
                continue

            scoreboard.append({
                "firebaseUid": user["firebaseUid"],
                "agoraUid": user["agoraUid"],
                "score": score
            })

        # 2️⃣ Ensure silent participants appear with 0 score
        for p in meta.get("participants", []):
            if not any(s["firebaseUid"] == p["firebaseUid"] for s in scoreboard):
                scoreboard.append({
                    "firebaseUid": p["firebaseUid"],
                    "agoraUid": p["agoraUid"],
                    "score": 0.0
                })




        result = {
    "idea": {
        "id": meta.get("ideaId"),
        "title": meta.get("ideaTitle"),
        "description": meta.get("ideaDescription")
    },

    "participants": meta.get("participants"),

    "winner": {
        "speaker": winner_speaker,
        "firebaseUid": winner_user["firebaseUid"] if winner_user else None,
        "agoraUid": winner_user["agoraUid"] if winner_user else None
    },

    "scores": {
        speaker_map[s]["firebaseUid"]: score
        for s, score in scores.items()
        if s in speaker_map
    },

    "summary": summary,
    "transcript": transcript,

    # 🔥 STEP 4 STARTS HERE
    "ui": {
        "winnerFirebaseUid": winner_user["firebaseUid"] if winner_user else None,
        "winnerAgoraUid": winner_user["agoraUid"] if winner_user else None,
        "scoreboard": scoreboard
    }
}



        with open(result_file, "w") as f:
            json.dump(result, f, indent=2)

        with open(job_file, "w") as f:
            json.dump({"status": "done"}, f)

        # ======================================
        # 🔥 WRITE CALL HISTORY TO FIRESTORE
        # ======================================

        idea_id = meta.get("ideaId")

        call_doc = {
            "jobId": job_id,
            "createdAt": meta.get("startedAt", firestore.SERVER_TIMESTAMP),
            "endedAt": firestore.SERVER_TIMESTAMP,

            "winnerFirebaseUid": winner_user["firebaseUid"] if winner_user else None,
            "winnerAgoraUid": winner_user["agoraUid"] if winner_user else None,

            "participants": meta.get("participants", []),

            # 🔥 Add these
            "scores": {
                speaker_map[s]["firebaseUid"]: score
                for s, score in scores.items()
                if s in speaker_map
            },
            "summary": summary,
            "winnerSpeaker": winner_speaker,

            "resultReady": True
        }


        db = get_firestore()

        db.collection("ideas") \
        .document(idea_id) \
        .collection("groupCalls") \
        .document("history") \
        .collection("calls") \
        .document(job_id) \
        .set(call_doc)


    except Exception as e:
        with open(job_file, "w") as f:
            json.dump(
                {"status": "error", "message": str(e)},
                f
            )

# =====================================================
# POST: create job
# =====================================================

@router.post("/debate")
async def analyze_debate(
    background_tasks: BackgroundTasks,
    audio: UploadFile = File(...),
    metadata: UploadFile = File(...)
):
    print("🔥 /analyze/debate HIT")

    job_id = str(uuid.uuid4())

    audio_path = os.path.join(
        UPLOAD_DIR, f"{job_id}_{audio.filename}"
    )
    metadata_path = os.path.join(
        UPLOAD_DIR, f"{job_id}_{metadata.filename}"
    )

    with open(audio_path, "wb") as f:
        shutil.copyfileobj(audio.file, f)

    with open(metadata_path, "wb") as f:
        shutil.copyfileobj(metadata.file, f)

    job_file = os.path.join(JOBS_DIR, f"{job_id}.json")
    with open(job_file, "w") as f:
        json.dump(
            {
                "status": "queued",
                "created_at": datetime.utcnow().isoformat()
            },
            f
        )

    background_tasks.add_task(
        run_debate_job,
        job_id,
        audio_path,
        metadata_path
    )

    return {
        "job_id": job_id,
        "status": "queued"
    }

# =====================================================
# POST: analyze group chat debate
# =============================================

@router.post("/groupchat")
def analyze_groupchat(payload: dict = Body(...)):
    print("🔥 /analyze/groupchat HIT")

    idea_id = payload.get("ideaId")

    if not idea_id:
        print("❌ error: ideaId missing")
        return {"status": "error", "message": "ideaId missing"}

    print(f"📖 Fetching idea details for ideaId: {idea_id}")
    db = get_firestore()

    # ======================================
    # FETCH IDEA DETAILS
    # ======================================

    idea_doc = db.collection("ideas").document(idea_id).get()

    if not idea_doc.exists:
        print("❌ error: Idea not found")
        return {"status": "error", "message": "Idea not found"}

    idea_data = idea_doc.to_dict()
    idea_title = idea_data.get("title", "Unknown Idea")
    print(f"✅ Idea loaded: {idea_title}")

    # ======================================
    # FETCH CHAT MESSAGES
    # ======================================

    print("📖 Fetching chat messages...")
    messages_ref = db.collection("ideas") \
                     .document(idea_id) \
                     .collection("messages") \
                     .order_by("timestamp")

    docs = messages_ref.stream()

    transcript = []
    participants = {}

    for doc in docs:
        data = doc.to_dict()

        uid = data.get("firebaseUid")
        username = data.get("username")
        text = data.get("message")

        if not uid or not text:
            continue

        transcript.append({
            "speaker": uid,
            "text": text
        })

        # Track participants
        if uid not in participants:
            participants[uid] = username

    if not transcript:
        print("❌ error: No messages found")
        return {"status": "error", "message": "No messages found"}
    
    print(f"✅ Found {len(transcript)} messages from {len(participants)} participants.")

    # ======================================
    # RUN EXISTING DEBATE MODEL
    # ======================================

    print("🚀 Loading debate analysis modules...")
    from backend.debate_analysis.feature_extraction import extract_features
    from backend.debate_analysis.scoring import score_speakers, decide_winner
    from backend.debate_analysis.summary import generate_summary

    print("🧠 Extracting features from transcript...")
    features = extract_features(transcript)
    print("✅ Feature extraction complete.")

    print("📊 Scoring speakers...")
    scores, ai_result = score_speakers(features, transcript)
    print("✅ Scoring complete.")

    print("🏆 Deciding winner...")
    winner_uid = decide_winner(scores)
    winner_name = participants.get(winner_uid, "Unknown")
    print(f"✅ Winner decided: {winner_name} (UID: {winner_uid})")

    print("📝 Generating debate summary...")
    # Generate summary using UID internally
    summary_raw = generate_summary(winner_uid, scores, features, transcript, ai_result=ai_result)
    print("✅ Summary generation complete.")

    # Replace UID mentions with usernames
    summary = summary_raw

    """for uid, username in participants.items():
        summary = summary.replace(uid, username)"""

    # ======================================
    # BUILD PARTICIPANT SCOREBOARD
    # ======================================

    print("📋 Building participant scoreboard...")
    scoreboard = []

    for uid, score in scores.items():
        scoreboard.append({
            "firebaseUid": uid,
            "username": participants.get(uid, "Unknown"),
            "score": score
        })

    # ======================================
    # FINAL RESULT STRUCTURE
    # ======================================

    print("📦 Assembling final result structure...")
    result = {
        "idea": {
            "id": idea_id,
            "title": idea_title
        },
        "winner": {
            "firebaseUid": winner_uid,
            "username": winner_name
        },
        "participants": scoreboard,
        "summary": summary
    }

    # ======================================
    # SAVE RESULT BACK TO FIRESTORE
    # ======================================

    print("💾 Saving result back to Firestore...")
    db.collection("ideas") \
      .document(idea_id) \
      .collection("debateResults") \
      .document("latest") \
      .set(result)

    print("🎉 Groupchat analysis successfully completed!")
    return {
        "status": "done",
        "result": result
    }


# =====================================================
# GET: poll result
# =====================================================

@router.get("/result/{job_id}")
def get_result(job_id: str):
    job_file = os.path.join(JOBS_DIR, f"{job_id}.json")
    result_file = os.path.join(JOBS_DIR, f"{job_id}_result.json")

    if not os.path.exists(job_file):
        return {"status": "not_found"}

    with open(job_file) as f:
        job = json.load(f)

    if job["status"] == "done" and os.path.exists(result_file):
        with open(result_file) as f:
            return {
                "status": "done",
                "result": json.load(f)
            }

    return job
