import json
import os
import tempfile
from pydub import AudioSegment


# -------------------------------------------------
# LAZY WHISPER MODEL LOADER
# -------------------------------------------------

_model = None


def _get_model():
    """
    Load faster-whisper model on first call (not at import time).
    """
    global _model

    if _model is not None:
        return _model

    from faster_whisper import WhisperModel

    print("🔄 Loading Whisper medium model (CPU int8) ...")
    print("   (Note: First run will download the ~1.5GB Whisper model.)")

    _model = WhisperModel(
        "medium",
        device="cpu",
        compute_type="int8"
    )

    print("✅ Whisper model loaded")

    return _model


# -------------------------------------------------
# AUDIO PREPROCESSING
# -------------------------------------------------

def _preprocess_audio(audio_path: str) -> str:
    """
    Normalize audio to 16kHz mono WAV for Whisper.
    Returns path to the preprocessed file.
    """
    audio = AudioSegment.from_file(audio_path)
    audio = audio.set_frame_rate(16000)
    audio = audio.set_channels(1)
    audio = audio.normalize()

    base, ext = os.path.splitext(audio_path)
    preprocessed_path = f"{base}_normalized.wav"
    audio.export(preprocessed_path, format="wav")

    return preprocessed_path


# -------------------------------------------------
# MAIN TRANSCRIPTION FUNCTION
# -------------------------------------------------

def transcribe_with_diarization(
    audio_path: str,
    diarization_path: str,
    output_path: str
):
    """
    Transcribe audio using diarization segments.

    For each diarization segment, extracts the audio chunk,
    transcribes it with Whisper, and maps it to the speaker.

    Args:
        audio_path:       Path to the original audio file
        diarization_path: Path to diarization JSON output
        output_path:      Path to save the final transcript JSON

    Returns:
        List of transcript segments: [{speaker, text, start, end}, ...]
    """

    if not os.path.exists(audio_path):
        raise FileNotFoundError(f"Audio file not found: {audio_path}")

    if not os.path.exists(diarization_path):
        raise FileNotFoundError(
            f"Diarization file not found: {diarization_path}"
        )

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    # Load diarization segments
    with open(diarization_path, "r") as f:
        diarization = json.load(f)

    if not diarization:
        print("⚠️ No diarization segments found, returning empty transcript")
        with open(output_path, "w") as f:
            json.dump([], f)
        return []

    # Normalize audio
    print("🔧 Preprocessing audio for transcription ...")
    preprocessed_path = _preprocess_audio(audio_path)
    audio = AudioSegment.from_file(preprocessed_path)

    model = _get_model()

    print(f"🎤 Transcribing {len(diarization)} diarization segments ...")

    raw_transcript = []

    for i, segment in enumerate(diarization):
        start_ms = int(segment["start"] * 1000)
        end_ms = int(segment["end"] * 1000)
        duration_sec = (end_ms - start_ms) / 1000.0

        # Skip very short segments (less than 0.5s — likely noise)
        if duration_sec < 0.5:
            continue

        # Extract audio chunk
        chunk = audio[start_ms:end_ms]

        # Use thread-safe temp file
        with tempfile.NamedTemporaryFile(
            suffix=".wav", delete=False
        ) as tmp:
            tmp_path = tmp.name
            chunk.export(tmp_path, format="wav")

        try:
            # Transcribe the chunk
            segments, _ = model.transcribe(
                tmp_path,
                language="en",
                beam_size=5,
                vad_filter=True,
                vad_parameters=dict(min_silence_duration_ms=500)
            )

            text = " ".join(seg.text for seg in segments).strip()
        finally:
            # Always clean up temp file
            try:
                os.remove(tmp_path)
            except OSError:
                pass

        # Skip empty transcriptions (no speech detected)
        if not text:
            continue

        raw_transcript.append({
            "speaker": segment["speaker"],
            "text": text,
            "start": round(segment["start"], 2),
            "end": round(segment["end"], 2)
        })

        # Progress logging every 5 segments
        if (i + 1) % 5 == 0 or (i + 1) == len(diarization):
            print(f"   Transcribed {i + 1}/{len(diarization)} segments")

    # -------------------------------------------------
    # MERGE CONSECUTIVE SAME-SPEAKER SEGMENTS
    # -------------------------------------------------

    merged_transcript = []

    for seg in raw_transcript:
        if (
            merged_transcript
            and merged_transcript[-1]["speaker"] == seg["speaker"]
        ):
            # Merge text and extend end time
            merged_transcript[-1]["text"] += " " + seg["text"]
            merged_transcript[-1]["end"] = seg["end"]
        else:
            merged_transcript.append(dict(seg))

    # Clean up preprocessed audio
    try:
        os.remove(preprocessed_path)
    except OSError:
        pass

    # Save output
    with open(output_path, "w") as f:
        json.dump(merged_transcript, f, indent=2)

    total_words = sum(
        len(seg["text"].split()) for seg in merged_transcript
    )
    speakers = set(seg["speaker"] for seg in merged_transcript)

    print(f"✅ Transcription complete:")
    print(f"   Segments: {len(merged_transcript)}")
    print(f"   Total words: {total_words}")
    print(f"   Speakers: {sorted(speakers)}")

    return merged_transcript
