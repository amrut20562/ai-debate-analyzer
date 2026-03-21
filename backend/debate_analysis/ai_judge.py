import json
import urllib.request
import urllib.error
import re

OLLAMA_URL = "http://localhost:11434/api/generate"
MODEL_NAME = "phi3:mini"

def _safe_float(val, default=5.0):
    try:
        return float(val)
    except:
        return default
def evaluate_debate(transcript):
    """
    Evaluates the debate using a local Ollama model.
    Takes the full debate transcript, groups by speaker, and returns a strict JSON.
    """
    if not transcript:
        return _get_fallback_result()

    # Group transcript by speaker for context
    speaker_lines = {}
    for segment in transcript:
        speaker = segment.get("speaker", "Unknown")
        text = segment.get("text", "").strip()
        if not text or "[no clear speech" in text.lower():
            continue
        if speaker not in speaker_lines:
            speaker_lines[speaker] = []
        speaker_lines[speaker].append(text)
    
    if not speaker_lines:
        return _get_fallback_result()

    debate_text = ""
    for speaker, lines in speaker_lines.items():
        debate_text += f"\n--- Speaker: {speaker} ---\n"
        debate_text += "\n".join(lines) + "\n"

    prompt = f"""You are an expert debate judge. Analyze this debate transcript and score each speaker.
RETURN ONLY STRICT JSON. NO MARKDOWN, NO EXPLANATIONS OUTSIDE THE JSON OBJECT.

Debate Transcript:
{debate_text}

Scoring Guidelines:
- 0-3 = very poor arguments
- 4-6 = average arguments
- 7-8 = strong arguments
- 9-10 = exceptional debate performance

Winner Selection Rules:
- The "winner" MUST be one of the provided SPEAKER_IDs.
- The winner MUST be the speaker with the strongest overall performance considering argument_quality, logic, and persuasiveness.
- Do NOT invent new speaker IDs.
- If unsure, choose the most reasonable winner instead of leaving it empty.
- Do not give equal scores to all speakers unless absolutely necessary.
- Differentiate clearly based on performance.

Output format requirement:
{{
  "speakers": {{
    "SPEAKER_ID": {{
      "argument_quality": <0-10 integer or float>,
      "logic": <0-10 integer or float>,
      "relevance": <0-10 integer or float>,
      "clarity": <0-10 integer or float>,
      "persuasiveness": <0-10 integer or float>
    }}
  }},
  "winner": "SPEAKER_ID",
  "winner_reason": "Short explanation why this speaker won",
  "judge_verdict": "<Detailed final verdict explaining who won and why>",
  "key_moments": [
    "<important turning point>",
    "<strong rebuttal>",
    "<logical flaw detected>"
  ]
}}
"""
    
    payload = {
        "model": MODEL_NAME,
        "prompt": prompt,
        "stream": False,
        "format": "json"
    }

    try:
        req = urllib.request.Request(
            OLLAMA_URL,
            data=json.dumps(payload).encode("utf-8"),
            headers={"Content-Type": "application/json"}
        )
        response = urllib.request.urlopen(req, timeout=15)
        response_body = response.read().decode("utf-8")
        result_json = json.loads(response_body)
        
        # Ollama returns the generated text in the 'response' field
        generated_text = result_json.get("response", "").strip()
        
        # Sometimes LLMs wrap JSON in markdown block even if format=json is set
        # 🔥 Remove markdown code blocks robustly
        generated_text = re.sub(r"^```json\s*", "", generated_text.strip())
        generated_text = re.sub(r"\s*```$", "", generated_text.strip())
        
        parsed_output = json.loads(generated_text)

        # ✅ FIRST validate speakers exists
        if "speakers" not in parsed_output:
            raise ValueError("Missing 'speakers' in AI output")

        speakers = parsed_output.get("speakers", {})

        ai_winner = parsed_output.get("winner")

        # ✅ Fix missing OR invalid winner
        if not ai_winner or ai_winner not in speakers:
            if speakers:
                best = max(
                    speakers.items(),
                    key=lambda x: _safe_float(x[1].get("argument_quality"))
                )[0]
                parsed_output["winner"] = best
            else:
                parsed_output["winner"] = None

        for speaker, data in parsed_output.get("speakers", {}).items():
            for key in ["argument_quality", "logic", "relevance", "clarity", "persuasiveness"]:
                val = data.get(key)

                if val is None or val == "":
                    data[key] = 5.0
                else:
                    try:
                        num = _safe_float(val)
                        data[key] = max(0.0, min(10.0, num))
                    except:
                        data[key] = 5.0
        
      
            
        return parsed_output

    except Exception as e:
        import traceback
        print("❌ AI Judge Error:")
        traceback.print_exc()
        print("⚠️ Using fallback AI scoring")
        return _get_fallback_result(speaker_lines.keys())

def _get_fallback_result(speakers=None):
    if not speakers:
        speakers = []
    
    result = {
        "speakers": {},
        "winner": next(iter(speakers)) if speakers else None,
        "winner_reason": "AI evaluation unavailable",
        "judge_verdict": "AI evaluation unavailable",
        "key_moments": ["AI evaluation failed. Fallback scores applied."]
    }
    
    for s in speakers:
        result["speakers"][s] = {
            "argument_quality": 5.0,
            "logic": 5.0,
            "relevance": 5.0,
            "clarity": 5.0,
            "persuasiveness": 5.0
        }
        
    return result
