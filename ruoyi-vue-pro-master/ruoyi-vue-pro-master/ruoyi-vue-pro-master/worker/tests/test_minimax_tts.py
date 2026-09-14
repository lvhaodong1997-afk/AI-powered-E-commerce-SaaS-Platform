from __future__ import annotations

import importlib
import os
from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient


VALID_PAYLOAD = {
    "text": "hello",
    "voice_setting": {"voice_id": "voice-1"},
}


@pytest.fixture()
def minimax_modules(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("MINIMAX_BCE_AK", "test-ak")
    monkeypatch.setenv("MINIMAX_BCE_SK", "test-sk")
    monkeypatch.setenv("TTS_INTERNAL_TOKEN", "test-token")

    import app.minimax_tts as minimax_tts
    import app.main as main

    importlib.reload(minimax_tts)
    importlib.reload(main)
    return minimax_tts, main


def test_health_and_task_routes_remain_available(minimax_modules):
    _, main = minimax_modules
    client = TestClient(main.app)

    assert client.get("/health").status_code == 200
    response = client.post(
        "/tasks/submit",
        json={
            "traceId": "trace-1",
            "tenantId": 1,
            "companyId": 2,
            "type": "RENDER",
        },
    )
    assert response.status_code == 200
    assert response.json() == {"accepted": True, "status": "PENDING"}


@pytest.mark.parametrize("header", [None, "wrong-token"])
def test_route_rejects_missing_or_invalid_internal_token(minimax_modules, header):
    _, main = minimax_modules
    client = TestClient(main.app)
    headers = {} if header is None else {"X-TTS-Internal-Token": header}

    response = client.post("/api/tts/minimax/generate", json=VALID_PAYLOAD, headers=headers)

    assert response.status_code == 401
    assert response.json() == {"detail": "invalid internal token"}


def test_route_rejects_legacy_internal_token_header(minimax_modules):
    _, main = minimax_modules
    response = TestClient(main.app).post(
        "/api/tts/minimax/generate",
        json=VALID_PAYLOAD,
        headers={"X-Internal-Token": "test-token"},
    )

    assert response.status_code == 401
    assert response.json() == {"detail": "invalid internal token"}


def test_non_ascii_token_is_rejected_as_unauthorized(minimax_modules):
    minimax_tts, _ = minimax_modules
    with pytest.raises(minimax_tts.HTTPException) as error:
        minimax_tts.generate(
            minimax_tts.MiniMaxTtsRequest(**VALID_PAYLOAD),
            x_tts_internal_token="invalid-é-token",
        )
    assert error.value.status_code == 401


def test_route_fails_closed_when_internal_token_is_not_configured(monkeypatch):
    monkeypatch.delenv("TTS_INTERNAL_TOKEN", raising=False)
    monkeypatch.setenv("MINIMAX_BCE_AK", "test-ak")
    monkeypatch.setenv("MINIMAX_BCE_SK", "test-sk")

    import app.minimax_tts as minimax_tts
    import app.main as main

    importlib.reload(minimax_tts)
    importlib.reload(main)
    response = TestClient(main.app).post(
        "/api/tts/minimax/generate",
        json=VALID_PAYLOAD,
        headers={"X-TTS-Internal-Token": "anything"},
    )

    assert response.status_code == 503
    assert response.json() == {"detail": "TTS service is not configured"}


def test_route_returns_reference_contract_with_defaults(minimax_modules):
    minimax_tts, main = minimax_modules
    upstream = {
        "data": {"audio": "https://audio.example.com/result.mp3"},
        "trace_id": "provider-trace",
        "extra_info": {"audio_length": 1000},
        "base_resp": {"status_code": 0, "status_msg": "success"},
    }
    with patch.object(minimax_tts, "_send_signed_request", return_value=upstream) as send:
        response = TestClient(main.app).post(
            "/api/tts/minimax/generate",
            json=VALID_PAYLOAD,
            headers={"X-TTS-Internal-Token": "test-token"},
        )

    assert response.status_code == 200
    assert response.json() == {
        "output": {"audio": {"url": "https://audio.example.com/result.mp3"}},
        "audio_url": "https://audio.example.com/result.mp3",
        "trace_id": "provider-trace",
        "extra_info": {"audio_length": 1000},
        "base_resp": {"status_code": 0, "status_msg": "success"},
    }
    request_body = send.call_args.args[0]
    assert request_body == {
        "model": "speech-2.8-turbo",
        "text": "hello",
        "output_format": "url",
        "subtitle_enable": False,
        "aigc_watermark": False,
        "language_boost": "auto",
        "voice_setting": {
            "voice_id": "voice-1",
            "speed": 1.2,
            "vol": 1.1,
            "pitch": 0,
            "emotion": "fluent",
        },
        "audio_setting": {
            "sample_rate": 32000,
            "bitrate": 128000,
            "format": "mp3",
            "channel": 1,
        },
    }


def test_signed_request_uses_real_bce_client_and_fixed_path(minimax_modules):
    minimax_tts, _ = minimax_modules
    captured = {}

    def fake_transport(self, method, path, headers=None, body=None, **kwargs):
        captured.update(
            client=self,
            method=method,
            path=path,
            headers=headers,
            body=body,
            config=self.config,
        )
        return {"base_resp": {"status_code": 0}}

    with patch.object(minimax_tts._MiniMaxClient, "_send_request", new=fake_transport):
        minimax_tts._send_signed_request({"text": "hello"})

    assert isinstance(captured["client"], minimax_tts._MiniMaxClient)
    assert captured["method"] == b"POST"
    assert captured["path"] == b"/v2/mini/t2a_v2"
    assert captured["config"].endpoint == b"vod.bj.baidubce.com"
    assert captured["config"].protocol is minimax_tts.HTTPS
    assert captured["config"].connection_timeout_in_mills == 180_000
    assert captured["config"].credentials.access_key_id == b"test-ak"
    assert captured["config"].credentials.secret_access_key == b"test-sk"
    assert isinstance(captured["config"].retry_policy, minimax_tts.NoRetryPolicy)


@pytest.mark.parametrize(
    "upstream",
    [
        {},
        {"base_resp": {}},
        {"base_resp": {"status_code": "0"}, "data": {"audio": "https://a.test/a.mp3"}},
        {"base_resp": {"status_code": 1, "status_msg": "secret test-sk failed"}},
        {"base_resp": {"status_code": 0}, "data": {}},
        {"base_resp": {"status_code": 0}, "data": {"audio": "deadbeef"}},
        {"base_resp": {"status_code": 0}, "data": {"audio": "http://a.test/a.mp3"}},
        {"base_resp": {"status_code": 0}, "data": {"audio": "https:///missing-host"}},
    ],
)
def test_route_rejects_invalid_upstream_without_leaking_secrets(minimax_modules, upstream):
    minimax_tts, main = minimax_modules
    with patch.object(minimax_tts, "_send_signed_request", return_value=upstream):
        response = TestClient(main.app).post(
            "/api/tts/minimax/generate",
            json=VALID_PAYLOAD,
            headers={"X-TTS-Internal-Token": "test-token"},
        )

    assert response.status_code == 502
    body = response.text
    assert "test-sk" not in body
    assert "test-ak" not in body
    assert response.json() == {"detail": "MiniMax TTS provider request failed"}


def test_route_redacts_unexpected_sdk_errors(minimax_modules):
    minimax_tts, main = minimax_modules
    with patch.object(
        minimax_tts,
        "_send_signed_request",
        side_effect=RuntimeError("credential test-ak/test-sk rejected"),
    ):
        response = TestClient(main.app).post(
            "/api/tts/minimax/generate",
            json=VALID_PAYLOAD,
            headers={"X-TTS-Internal-Token": "test-token"},
        )

    assert response.status_code == 502
    assert response.json() == {"detail": "MiniMax TTS provider request failed"}
    assert "test-ak" not in response.text
    assert "test-sk" not in response.text
