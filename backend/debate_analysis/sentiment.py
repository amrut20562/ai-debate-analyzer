from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

MODEL_NAME = "distilbert-base-uncased-finetuned-sst-2-english"

_tokenizer = None
_model = None


def load_model():
    global _tokenizer, _model

    if _tokenizer is None:
        print("🔄 Loading DistilBERT tokenizer...")
        _tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)

    if _model is None:
        print("🔄 Loading DistilBERT sentiment model...")
        _model = AutoModelForSequenceClassification.from_pretrained(MODEL_NAME)
        _model.eval()

    return _tokenizer, _model


def analyze_sentiment(text):

    tokenizer, model = load_model()

    inputs = tokenizer(
        text,
        return_tensors="pt",
        truncation=True,
        padding=True,
        max_length=128
    )

    with torch.no_grad():
        outputs = model(**inputs)

    logits = outputs.logits
    probs = torch.softmax(logits, dim=1)

    confidence, label = torch.max(probs, dim=1)

    confidence = confidence.item()
    label = label.item()

    if label == 1:
        sentiment = "Positive"
    else:
        sentiment = "Negative"

    # treat low confidence as neutral
    if confidence < 0.60:
        sentiment = "Neutral"

    return sentiment, confidence