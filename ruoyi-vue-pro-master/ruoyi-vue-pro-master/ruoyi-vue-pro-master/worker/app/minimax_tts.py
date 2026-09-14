from __future__ import annotations

import hmac
import json
import os
from typing import Any, Literal
from urllib.parse import urlparse

from baidubce.auth.bce_credentials import BceCredentials
from baidubce.bce_base_client import BceBaseClient
from baidubce.bce_client_configuration import BceClientConfiguration
from baidubce.protocol import HTTPS
from baidubce.retry.retry_policy import NoRetryPolicy
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field


def _timeout_seconds() -> int:
    try:
        return max(10, int(os.getenv("MINIMAX_BCE_TIMEOUT_SECONDS", "180")))
    except ValueError:
        return 180


MINIMAX_BCE_AK = os.getenv("MINIMAX_BCE_AK", "").strip()
MINIMAX_BCE_SK = os.getenv("MINIMAX_BCE_SK", "").strip()
MINIMAX_BCE_ENDPOINT = os.getenv("MINIMAX_BCE_ENDPOINT", "vod.bj.baidubce.com").strip()
MINIMAX_BCE_TIMEOUT_SECONDS = _timeout_seconds()
TTS_INTERNAL_TOKEN = os.getenv("TTS_INTERNAL_TOKEN", "").strip()


class MiniMaxVoiceSetting(BaseModel):
    voice_id: str = Field(min_length=1, max_length=128)
    speed: float = Field(default=1.2, ge=0.5, le=2.0)
    vol: float = Field(default=1.1, gt=0, le=10)
    pitch: int = Field(default=0, ge=-12, le=12)
    emotion: str = Field(default="fluent", min_length=1, max_length=32)


class MiniMaxAudioSetting(BaseModel):
    sample_rate: int = Field(default=32000, gt=0)
    bitrate: int = Field(default=128000, gt=0)
    format: Literal["mp3"] = "mp3"
    channel: Literal[1] = 1


class MiniMaxTtsRequest(BaseModel):
    model: str = Field(default="speech-2.8-turbo", min_length=1, max_length=64)
    text: str = Field(min_length=1, max_length=10000)
    output_format: Literal["url"] = "url"
    subtitle_enable: Literal[False] = False
    aigc_watermark: Literal[False] = False
    language_boost: str = Field(default="auto", min_length=1, max_length=32)
    voice_setting: MiniMaxVoiceSetting
    audio_setting: MiniMaxAudioSetting = Field(default_factory=MiniMaxAudioSetting)


class MiniMaxTtsError(RuntimeError):
    pass


class _MiniMaxClient(BceBaseClient):
    pass


_MiniMaxClient.__module__ = "baidubce.services.minimax.mini_client"


def _to_plain(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): _to_plain(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_to_plain(item) for item in value]
    if hasattr(value, "__dict__"):
        return {
            key: _to_plain(item)
            for key, item in vars(value).items()
            if not key.startswith("_")
        }
    return value


def _build_client() -> _MiniMaxClient:
    if not MINIMAX_BCE_AK or not MINIMAX_BCE_SK:
        raise MiniMaxTtsError("credentials are not configured")
    if not MINIMAX_BCE_ENDPOINT or "://" in MINIMAX_BCE_ENDPOINT or "/" in MINIMAX_BCE_ENDPOINT:
        raise MiniMaxTtsError("endpoint is invalid")
    config = BceClientConfiguration(
        credentials=BceCredentials(MINIMAX_BCE_AK, MINIMAX_BCE_SK),
        endpoint=MINIMAX_BCE_ENDPOINT,
        protocol=HTTPS,
        connection_timeout_in_mills=MINIMAX_BCE_TIMEOUT_SECONDS * 1000,
        retry_policy=NoRetryPolicy(),
    )
    return _MiniMaxClient(config)


def _send_signed_request(request_body: dict[str, Any]) -> dict[str, Any]:
    response = _build_client()._send_request(
        b"POST",
        b"/v2/mini/t2a_v2",
        headers={b"Content-Type": b"application/json; charset=utf-8"},
        body=json.dumps(request_body, ensure_ascii=False),
    )
    plain = _to_plain(response)
    if not isinstance(plain, dict):
        raise MiniMaxTtsError("provider response is malformed")
    return plain


def _is_https_url(value: Any) -> bool:
    if not isinstance(value, str) or not value.strip():
        return False
    parsed = urlparse(value.strip())
    return parsed.scheme == "https" and bool(parsed.hostname)


def generate_minimax_speech(payload: MiniMaxTtsRequest) -> dict[str, Any]:
    plain = _send_signed_request(payload.model_dump())
    base_resp = plain.get("base_resp")
    status_code = base_resp.get("status_code") if isinstance(base_resp, dict) else None
    if type(status_code) is not int or status_code != 0:
        raise MiniMaxTtsError("provider rejected the request")

    data = plain.get("data")
    audio_url = data.get("audio") if isinstance(data, dict) else None
    if not _is_https_url(audio_url):
        raise MiniMaxTtsError("provider response has no HTTPS audio URL")
    audio_url = audio_url.strip()
    return {
        "output": {"audio": {"url": audio_url}},
        "audio_url": audio_url,
        "trace_id": plain.get("trace_id"),
        "extra_info": plain.get("extra_info") or {},
        "base_resp": base_resp,
    }


router = APIRouter(prefix="/api/tts/minimax", tags=["tts"])


@router.post("/generate", summary="Generate speech through MiniMax with BCE signed authentication")
def generate(
    payload: MiniMaxTtsRequest,
    x_tts_internal_token: str | None = Header(default=None),
) -> dict[str, Any]:
    if not TTS_INTERNAL_TOKEN:
        raise HTTPException(status_code=503, detail="TTS service is not configured")
    if x_tts_internal_token is None or not hmac.compare_digest(
        x_tts_internal_token.encode("utf-8"),
        TTS_INTERNAL_TOKEN.encode("utf-8"),
    ):
        raise HTTPException(status_code=401, detail="invalid internal token")
    try:
        return generate_minimax_speech(payload)
    except Exception as exc:
        raise HTTPException(
            status_code=502,
            detail="MiniMax TTS provider request failed",
        ) from exc
