#!/usr/bin/env python3
import argparse
import json
import subprocess
import sys


def video_duration(path):
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


def fallback_analysis(path, interval):
    duration = max(video_duration(path), interval)
    frames = []
    t = 0.0
    while t <= duration:
        frames.append(
            {
                "time": round(t, 2),
                "boxes": [
                    {
                        "label": "center_subject",
                        "x": 270,
                        "y": 520,
                        "w": 540,
                        "h": 760,
                        "score": 0.35,
                    }
                ],
            }
        )
        t += interval
    return {"centerSubjectLikely": True, "frames": frames}


def yolo_analysis(args):
    import cv2
    from ultralytics import YOLO

    model = YOLO(args.model or "yolov8n.pt")
    cap = cv2.VideoCapture(args.video)
    fps = cap.get(cv2.CAP_PROP_FPS) or 30
    frame_step = max(1, int(fps * args.frame_interval))
    frames = []
    frame_index = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break
        if frame_index % frame_step != 0:
            frame_index += 1
            continue
        timestamp = frame_index / fps
        results = model.predict(frame, verbose=False, imgsz=640)
        boxes = []
        for result in results:
            names = result.names
            for box in result.boxes:
                x1, y1, x2, y2 = [int(v) for v in box.xyxy[0].tolist()]
                cls = int(box.cls[0])
                label = names.get(cls, str(cls))
                score = float(box.conf[0])
                if score < 0.25:
                    continue
                boxes.append(
                    {
                        "label": label,
                        "x": x1,
                        "y": y1,
                        "w": max(0, x2 - x1),
                        "h": max(0, y2 - y1),
                        "score": round(score, 3),
                    }
                )
        frames.append({"time": round(timestamp, 2), "boxes": boxes})
        frame_index += 1
    cap.release()
    return {"centerSubjectLikely": True, "frames": frames}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--video", required=True)
    parser.add_argument("--clip-plan")
    parser.add_argument("--frame-interval", type=float, default=1.0)
    parser.add_argument("--model")
    args = parser.parse_args()
    try:
        analysis = yolo_analysis(args)
        if not analysis.get("frames"):
            raise RuntimeError("empty visual analysis")
    except Exception:
        analysis = fallback_analysis(args.video, max(0.5, args.frame_interval))
    json.dump(analysis, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    main()
