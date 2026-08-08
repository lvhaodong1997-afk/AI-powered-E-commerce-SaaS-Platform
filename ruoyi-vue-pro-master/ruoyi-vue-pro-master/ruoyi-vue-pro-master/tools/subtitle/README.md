# TK Smart Subtitle Scripts

These scripts are optional adapters for the Java subtitle pipeline. The backend
falls back to rule-based timing and layout when they are disabled or missing.

## ASR word timing

Script:

```bash
python tools/subtitle/asr_faster_whisper.py \
  --audio voice.mp3 \
  --language zh-cn \
  --text "口播文案" \
  --keywords '["防晒","99元"]' \
  --model small
```

Optional dependency:

```bash
pip install faster-whisper
```

Expected output:

```json
{
  "language": "zh-cn",
  "audioDuration": 12.34,
  "segments": [
    {
      "text": "这款防晒衣真的很轻薄",
      "start": 0.12,
      "end": 2.86,
      "words": [
        { "text": "这", "start": 0.12, "end": 0.25, "keyword": false },
        { "text": "防晒", "start": 0.38, "end": 0.72, "keyword": true }
      ]
    }
  ]
}
```

## Visual avoidance

Script:

```bash
python tools/subtitle/visual_yolo_detect.py \
  --video merged-video.mp4 \
  --frame-interval 1.0 \
  --model yolov8n.pt
```

Optional dependencies:

```bash
pip install ultralytics opencv-python
```

Expected output:

```json
{
  "centerSubjectLikely": true,
  "frames": [
    {
      "time": 1.0,
      "boxes": [
        { "label": "person", "x": 320, "y": 240, "w": 360, "h": 760, "score": 0.92 },
        { "label": "product", "x": 300, "y": 930, "w": 420, "h": 360, "score": 0.72 }
      ]
    }
  ]
}
```

## Backend configuration

```yaml
tk:
  generation:
    subtitle:
      asr:
        enabled: true
        python: python3
        script-path: tools/subtitle/asr_faster_whisper.py
        model: small
        timeout-seconds: 300
      visual:
        enabled: true
        python: python3
        script-path: tools/subtitle/visual_yolo_detect.py
        model-path: yolov8n.pt
        frame-interval-seconds: 1.0
        timeout-seconds: 300
```
