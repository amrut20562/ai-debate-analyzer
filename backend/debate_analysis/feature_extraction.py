import re

# Simple list of words that indicate confidence / assertiveness
CONFIDENCE_WORDS = [
    "must", "will", "clearly", "definitely",
    "always", "never", "important", "strong",
    "prove", "evidence", "fact", "agree", "disagree"
]


def extract_features(transcript):
    """
    Extract linguistic debate features per speaker
    Input: transcript (list of {speaker, text})
    Output: dict with features per speaker
    """

    features = {}

    for entry in transcript:
        speaker = entry["speaker"]
        text = entry["text"]

        # Skip placeholder / silence lines
        if "[no clear speech" in text.lower():
            continue

        if speaker not in features:
            features[speaker] = {
                "word_count": 0,
                "sentence_count": 0,
                "confidence_score": 0
            }

        # --- WORD COUNT ---
        words = text.split()
        features[speaker]["word_count"] += len(words)

        # --- SENTENCE COUNT ---
        sentences = re.split(r"[.!?]", text)
        sentences = [s for s in sentences if s.strip()]
        features[speaker]["sentence_count"] += len(sentences)

        # --- CONFIDENCE SCORE ---
        text_lower = text.lower()
        confidence_hits = sum(
            1 for w in CONFIDENCE_WORDS if w in text_lower
        )
        features[speaker]["confidence_score"] += confidence_hits

    return features
