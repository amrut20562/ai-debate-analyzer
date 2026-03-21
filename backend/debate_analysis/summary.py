import json
from collections import defaultdict
from backend.debate_analysis.sentiment import analyze_sentiment


def generate_summary(winner_uid, scores, features, transcript, speaker_to_uid=None, ai_result=None):

    if speaker_to_uid is None:
        speaker_to_uid = {}

    conversation_with_sentiment = []
    speaker_stats = defaultdict(lambda: {
        "total_sentences": 0,
        "positive_sentences": 0,
        "negative_sentences": 0,
        "neutral_sentences": 0
    })

    total_positive = 0
    total_negative = 0
    total_neutral = 0

    speakers = set()

    # --------------------------------
    # Sentiment analysis per segment
    # --------------------------------

    for segment in transcript:

        speaker = segment["speaker"]
        text = segment["text"]

        speakers.add(speaker)

        sentiment, confidence = analyze_sentiment(text)

        conversation_with_sentiment.append({
            "speaker": speaker,
            "firebaseUid": speaker_to_uid.get(speaker),
            "text": text,
            "sentiment": sentiment,
            "confidence": confidence
        })

        speaker_stats[speaker]["total_sentences"] += 1

        if sentiment == "Positive":
            speaker_stats[speaker]["positive_sentences"] += 1
            total_positive += 1

        elif sentiment == "Negative":
            speaker_stats[speaker]["negative_sentences"] += 1
            total_negative += 1

        else:
            speaker_stats[speaker]["neutral_sentences"] += 1
            total_neutral += 1

    total_sentences = total_positive + total_negative + total_neutral

    if total_sentences == 0:
        total_sentences = 1

    debate_sentiment_distribution = {
        "positive_percent": round((total_positive / total_sentences) * 100, 2),
        "negative_percent": round((total_negative / total_sentences) * 100, 2),
        "neutral_percent": round((total_neutral / total_sentences) * 100, 2)
    }

    # --------------------------------
    # Speaker rankings
    # --------------------------------

    most_aggressive = None
    most_constructive = None
    most_neutral = None
    most_emotional = None
    most_balanced = None

    max_negative_ratio = -1
    max_positive_ratio = -1
    max_neutral_ratio = -1
    max_emotional = -1
    min_balance_diff = 999

    for speaker, stats in speaker_stats.items():

        total = stats["total_sentences"]

        pos_ratio = stats["positive_sentences"] / total
        neg_ratio = stats["negative_sentences"] / total
        neu_ratio = stats["neutral_sentences"] / total

        emotional = pos_ratio + neg_ratio
        balance_diff = abs(pos_ratio - neg_ratio)

        if neg_ratio > max_negative_ratio:
            max_negative_ratio = neg_ratio
            most_aggressive = speaker

        if pos_ratio > max_positive_ratio:
            max_positive_ratio = pos_ratio
            most_constructive = speaker

        if neu_ratio > max_neutral_ratio:
            max_neutral_ratio = neu_ratio
            most_neutral = speaker

        if emotional > max_emotional:
            max_emotional = emotional
            most_emotional = speaker

        if balance_diff < min_balance_diff:
            min_balance_diff = balance_diff
            most_balanced = speaker

    speaker_rankings = {
        "most_aggressive_speaker": most_aggressive,
        "most_constructive_speaker": most_constructive,
        "most_neutral_speaker": most_neutral,
        "most_emotional_speaker": most_emotional,
        "most_balanced_speaker": most_balanced
    }

    metadata = {
        "total_speakers": len(speakers),
        "total_segments": len(transcript),
        #"debate_duration_seconds": transcript[-1].get("end", 0) if transcript else 0
        "debate_duration_seconds": transcript[-1].get("timestamp_seconds", {}).get("end", 0)

        
    }

    summary = {
        "metadata": metadata,
        "scores": scores,
        "features": features,
        "speaker_mapping": speaker_to_uid,
        "conversation_with_sentiment": conversation_with_sentiment,
        "speaker_sentiment_stats": speaker_stats,
        "debate_sentiment_distribution": debate_sentiment_distribution,
        "speaker_rankings": speaker_rankings,
        "winner": winner_uid
    }

    if ai_result:
        summary["ai_judge"] = {
            "winner": ai_result.get("winner"),
            "winner_reason": ai_result.get("winner_reason"),
            "judge_verdict": ai_result.get("judge_verdict", "AI evaluation unavailable"),
            "key_moments": ai_result.get("key_moments", []),
            "speaker_analysis": ai_result.get("speakers", {})
        }

        return summary