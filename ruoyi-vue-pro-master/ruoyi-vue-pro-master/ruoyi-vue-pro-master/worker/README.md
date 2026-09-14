# TK Media Worker

## Local startup

Install runtime dependencies and start the FastAPI application from this directory:

```powershell
python -m pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

## MiniMax BCE TTS

`POST /api/tts/minimax/generate` calls MiniMax through the Baidu BCE signed SDK at
`https://vod.bj.baidubce.com/v2/mini/t2a_v2`. Send the internal credential in the
`X-TTS-Internal-Token` header. The endpoint is unavailable when `TTS_INTERNAL_TOKEN`
is empty.

Required environment variables:

- `MINIMAX_BCE_AK`
- `MINIMAX_BCE_SK`
- `TTS_INTERNAL_TOKEN`

Optional environment variables:

- `MINIMAX_BCE_ENDPOINT` (default: `vod.bj.baidubce.com`, hostname only)
- `MINIMAX_BCE_TIMEOUT_SECONDS` (default: `180`, minimum: `10`)

Minimal request:

```json
{
  "text": "hello",
  "voice_setting": {
    "voice_id": "your-voice-id"
  }
}
```

The successful response preserves the reference contract:

```json
{
  "output": {"audio": {"url": "https://example/audio.mp3"}},
  "audio_url": "https://example/audio.mp3",
  "trace_id": "provider-trace-id",
  "extra_info": {},
  "base_resp": {"status_code": 0}
}
```

The Worker accepts only a provider `base_resp.status_code` integer equal to zero
and an actual HTTPS audio URL. Provider details and configured credentials are
not returned in error responses. Automatic BCE retries are disabled because TTS
generation is a paid operation.
