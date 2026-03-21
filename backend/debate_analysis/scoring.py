from backend.debate_analysis.ai_judge import evaluate_debate

def safe_float(value, default=5.0):
    try:
        if value is None:
            return default
        return float(value)
    except:
        return default

def score_speakers(features, transcript=None):
    """
    Compute final score per speaker using AI judge
    """
    scores = {}

    ai_result = None
    if transcript:
        ai_result = evaluate_debate(transcript)

    # Initialize safe fallback if evaluate_debate failed or wasn't called
    if not ai_result or not isinstance(ai_result, dict):
        ai_result = {}

    if "speakers" not in ai_result or not isinstance(ai_result.get("speakers"), dict):
        ai_result["speakers"] = {}

    ai_speakers = ai_result.get("speakers", {})

    all_speakers = set(features.keys()) | set(ai_speakers.keys())

    for speaker in all_speakers:
        speaker_ai = ai_speakers.get(speaker, {})
        
        arg_qual = safe_float(speaker_ai.get("argument_quality"))
        logic = safe_float(speaker_ai.get("logic"))
        relevance = safe_float(speaker_ai.get("relevance"))
        clarity = safe_float(speaker_ai.get("clarity"))
        persuasiveness = safe_float(speaker_ai.get("persuasiveness"))
        
        #score =  safe_float(speaker_ai.get("argument_quality")) * 10
        score = (
            arg_qual +
            logic +
            relevance +
            clarity +
            persuasiveness
        ) / 5 * 10

        scores[speaker] = round(score, 2)
        
        # Ensure that if the speaker wasn't in ai_speakers, we add them with defaults
        if speaker not in ai_speakers:
            ai_result["speakers"][speaker] = {
                "argument_quality": arg_qual,
                "logic": logic,
                "relevance": relevance,
                "clarity": clarity,
                "persuasiveness": persuasiveness
            }


    # 🔥 Safety: ensure at least one score exists
    if not scores and ai_speakers:
        for speaker in ai_speakers.keys():
            scores[speaker] = 5.0
    return scores, ai_result


def decide_winner(scores, ai_result=None):

    if ai_result:
        ai_winner = ai_result.get("winner")

        # ✅ Ensure AI winner is valid speaker
        if ai_winner and ai_winner in scores:
            return ai_winner

        # 🔥 fallback 1: highest argument_quality
        ai_speakers = ai_result.get("speakers", {})

        valid_speakers = {
            s: v for s, v in ai_speakers.items() if s in scores
        }

        if valid_speakers:
            best = max(
                valid_speakers.items(),
                key=lambda x: (
                    safe_float(x[1].get("argument_quality")) +
                    safe_float(x[1].get("logic")) +
                    safe_float(x[1].get("persuasiveness"))
                )
            )[0]
            return best 

    # 🔥 final fallback (never crash)
    return max(scores, key=scores.get) if scores else None