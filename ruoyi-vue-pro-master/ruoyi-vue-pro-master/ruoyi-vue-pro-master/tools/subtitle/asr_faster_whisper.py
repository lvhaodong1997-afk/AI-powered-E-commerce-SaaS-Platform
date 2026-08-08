#!/usr/bin/env python3
import argparse
import json
import math
import os
import re
import subprocess
import sys

os.environ.setdefault("HF_HUB_DISABLE_XET", "1")


def audio_duration(path):
    try:
        result = subprocess.run(
            [
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                path,
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=30,
        )
        return float(result.stdout.strip())
    except Exception:
        return 0.0


def split_sentences(text):
    items = re.split(r"(?<=[。！？!?；;])", text.replace("\r", "").replace("\n", " "))
    return [item.strip() for item in items if item.strip()]


def split_tokens(text):
    tokens = []
    ascii_buf = []
    for ch in text:
        if ord(ch) < 128 and (ch.isalnum() or ch in "'-%"):
            ascii_buf.append(ch)
            continue
        if ascii_buf:
            tokens.append("".join(ascii_buf))
            ascii_buf.clear()
        if not ch.isspace():
            tokens.append(ch)
    if ascii_buf:
        tokens.append("".join(ascii_buf))
    return tokens


def mark_keywords(words, keywords):
    full = "".join(word["text"] for word in words)
    lower = full.lower()
    for keyword in keywords:
        if not keyword:
            continue
        search_from = 0
        while True:
            start = lower.find(keyword.lower(), search_from)
            if start < 0:
                break
            end = start + len(keyword)
            cursor = 0
            for word in words:
                next_cursor = cursor + len(word["text"])
                if next_cursor > start and cursor < end:
                    word["keyword"] = True
                cursor = next_cursor
            search_from = end


def fallback_timeline(text, language, duration, keywords):
    sentences = split_sentences(text) or [text[:80]]
    total_weight = sum(max(1, len(item)) for item in sentences)
    cursor = 0.0
    segments = []
    for index, sentence in enumerate(sentences):
        if index == len(sentences) - 1:
            end = duration
        else:
            end = min(duration, cursor + max(1.2, duration * max(1, len(sentence)) / max(1, total_weight)))
        tokens = split_tokens(sentence)
        token_cursor = cursor
        words = []
        token_weight = sum(max(1, len(item)) for item in tokens)
        for token_index, token in enumerate(tokens):
            token_end = end if token_index == len(tokens) - 1 else min(
                end, token_cursor + max(0.08, (end - cursor) * max(1, len(token)) / max(1, token_weight))
            )
            words.append(
                {
                    "text": token,
                    "start": round(token_cursor, 2),
                    "end": round(token_end, 2),
                    "keyword": False,
                }
            )
            token_cursor = token_end
        mark_keywords(words, keywords)
        segments.append(
            {
                "text": sentence,
                "start": round(cursor, 2),
                "end": round(max(end, cursor + 1.2), 2),
                "words": words,
            }
        )
        cursor = end
        if cursor >= duration:
            break
    return {"language": language, "audioDuration": round(duration, 2), "segments": segments}


def faster_whisper_timeline(args, keywords):
    from faster_whisper import WhisperModel

    model = WhisperModel(args.model, device="auto", compute_type="auto")
    segments, info = model.transcribe(
        args.audio,
        word_timestamps=True,
        language=whisper_language(args.language),
        initial_prompt=args.text or None,
    )
    result_segments = []
    for segment in segments:
        words = []
        text_parts = []
        for word in segment.words or []:
            token = word.word.strip()
            if not token:
                continue
            text_parts.append(token)
            words.append(
                {
                    "text": token,
                    "start": round(float(word.start), 2),
                    "end": round(float(word.end), 2),
                    "keyword": False,
                }
            )
        text = "".join(text_parts) if args.language.startswith("zh") else " ".join(text_parts)
        if not text:
            text = segment.text.strip()
        mark_keywords(words, keywords)
        result_segments.append(
            {
                "text": text,
                "start": round(float(segment.start), 2),
                "end": round(float(segment.end), 2),
                "words": words,
            }
        )
    return {
        "language": args.language,
        "audioDuration": round(float(getattr(info, "duration", 0.0) or audio_duration(args.audio)), 2),
        "segments": result_segments,
    }


def whisper_language(language):
    value = (language or "").lower()
    if value.startswith("zh"):
        return "zh"
    if value.startswith("en"):
        return "en"
    if value.startswith("es"):
        return "es"
    if value.startswith("fr"):
        return "fr"
    if value.startswith("nl"):
        return "nl"
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--audio", required=True)
    parser.add_argument("--language", default="")
    parser.add_argument("--text", default="")
    parser.add_argument("--keywords", default="[]")
    parser.add_argument("--model", default="small")
    args = parser.parse_args()
    try:
        keywords = json.loads(args.keywords) if args.keywords else []
    except Exception:
        keywords = []
    duration = audio_duration(args.audio)
    if duration <= 0:
        duration = max(3.0, math.ceil(len(args.text) / 8))
    try:
        timeline = faster_whisper_timeline(args, keywords)
        if not timeline.get("segments"):
            raise RuntimeError("empty ASR result")
    except Exception as exc:
        print(f"ASR failed: {exc}", file=sys.stderr)
        sys.exit(2)
    json.dump(timeline, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    main()
