import os
import json
import torch
from pydub import AudioSegment
from pyannote.audio import Pipeline


# -------------------------------------------------
# LAZY PIPELINE LOADER
# -------------------------------------------------

_pipeline = None


def _get_pipeline():
    """
    Load pyannote speaker-diarization-3.1 on first call.
    Requires HF_AUTH_TOKEN env variable.
    """
    global _pipeline

    if _pipeline is not None:
        return _pipeline

    hf_token = os.environ.get("HF_AUTH_TOKEN")

    if not hf_token:
        raise RuntimeError(
            "HF_AUTH_TOKEN environment variable is required. "
            "Get your token from https://huggingface.co/settings/tokens "
            "and accept the model license at "
            "https://huggingface.co/pyannote/speaker-diarization-3.1"
        )

    print("🔄 Loading pyannote speaker-diarization-3.1 ...")
    print("   (Note: First run will download ~1.5GB of models. This may take several minutes.)")

    _pipeline = Pipeline.from_pretrained(
        "pyannote/speaker-diarization-3.1",
        use_auth_token=hf_token
    )

    # Use GPU if available, otherwise CPU
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    _pipeline = _pipeline.to(device)

    device_name = "GPU (CUDA)" if torch.cuda.is_available() else "CPU"
    print(f"✅ Diarization pipeline loaded on {device_name}")

    return _pipeline


# -------------------------------------------------
# AUDIO PREPROCESSING
# -------------------------------------------------

def _preprocess_audio(audio_path: str) -> str:
    """
    Convert audio to 16kHz mono WAV (required by pyannote).
    Returns path to the preprocessed file.
    """
    audio = AudioSegment.from_file(audio_path)
    audio = audio.set_frame_rate(16000)
    audio = audio.set_channels(1)
    audio = audio.normalize()

    # Save preprocessed audio alongside the original
    base, ext = os.path.splitext(audio_path)
    preprocessed_path = f"{base}_16k_mono.wav"
    audio.export(preprocessed_path, format="wav")

    return preprocessed_path


# -------------------------------------------------
# MAIN DIARIZATION FUNCTION
# -------------------------------------------------

def run_diarization(
    audio_path: str,
    output_path: str,
    num_speakers: int = None
):
    """
    Run real speaker diarization using pyannote/speaker-diarization-3.1.

    Args:
        audio_path:    Path to the audio file
        output_path:   Path to save diarization JSON output
        num_speakers:  Optional hint for expected number of speakers.
                       If None, pyannote detects automatically.

    Returns:
        List of diarization segments: [{speaker, start, end}, ...]
    """

    if not os.path.exists(audio_path):
        raise FileNotFoundError(f"Audio file not found: {audio_path}")

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    # Preprocess audio to 16kHz mono
    print("🔧 Preprocessing audio for diarization ...")
    preprocessed_path = _preprocess_audio(audio_path)

    # Run pyannote diarization
    pipeline = _get_pipeline()

    print("🎙️ Running speaker diarization ...")

    # Build pipeline params
    params = {}
    if num_speakers is not None and num_speakers >= 2:
        params["num_speakers"] = num_speakers
        print(f"   Speaker hint: expecting {num_speakers} speakers")

    diarization = pipeline(preprocessed_path, **params)

    # Convert pyannote output to our JSON format
    results = []

    for turn, _, speaker in diarization.itertracks(yield_label=True):
        results.append({
            "speaker": speaker,
            "start": round(turn.start, 2),
            "end": round(turn.end, 2)
        })

    # Merge consecutive segments from the same speaker
    merged = []

    for seg in results:
        if (
            merged
            and merged[-1]["speaker"] == seg["speaker"]
            and seg["start"] - merged[-1]["end"] < 0.5
        ):
            # Extend previous segment
            merged[-1]["end"] = seg["end"]
        else:
            merged.append(dict(seg))

    # Clean up preprocessed file
    try:
        os.remove(preprocessed_path)
    except OSError:
        pass

    # Save output
    with open(output_path, "w") as f:
        json.dump(merged, f, indent=2)

    speaker_ids = set(seg["speaker"] for seg in merged)
    total_duration = sum(seg["end"] - seg["start"] for seg in merged)

    print(f"✅ Diarization complete:")
    print(f"   Speakers detected: {len(speaker_ids)} → {sorted(speaker_ids)}")
    print(f"   Total segments: {len(merged)}")
    print(f"   Total speech: {round(total_duration, 1)}s")

    return merged
