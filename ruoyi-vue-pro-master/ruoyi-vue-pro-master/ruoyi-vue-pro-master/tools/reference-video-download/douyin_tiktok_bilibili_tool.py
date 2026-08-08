#!/usr/bin/env python3
"""
Small wrapper around Evil0ctal/Douyin_TikTok_Download_API.

Features:
  1. Parse one Douyin/TikTok/Bilibili URL into normalized structured JSON.
  2. Generate platform request parameters such as X-Bogus, a_bogus, msToken,
     and Bilibili w_rid.
  3. Provide a lightweight FastAPI service with /api/parse, /api/signature,
     and /api/download.

This script reuses the upstream crawler/signature code instead of copying it.
Install upstream requirements first:
  python -m pip install -r path/to/Douyin_TikTok_Download_API/requirements.txt

Examples:
  python douyin_tiktok_bilibili_tool.py parse "https://v.douyin.com/xxx/"
  python douyin_tiktok_bilibili_tool.py download "https://www.bilibili.com/video/BV..." --out-dir downloads
  python douyin_tiktok_bilibili_tool.py serve --host 127.0.0.1 --port 8000
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import html as html_lib
import importlib
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from pathlib import Path
from typing import Any
from urllib.parse import parse_qsl, quote, urlencode, urlparse, urlunparse


DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/122.0.0.0 Safari/537.36"
)
DEFAULT_PARSE_TIMEOUT_SECONDS = 45
DEFAULT_YTDLP_TIMEOUT_SECONDS = 240
DEFAULT_DOUYIN_COOKIE_TIMEOUT_SECONDS = 45
VIDEO_SIGNATURE_BYTES = 512
DOUYIN_FRESH_COOKIE_MARKERS = (
    "fresh cookies",
    "s_v_web_id",
)
SUPPORTED_SOURCE_HOST_MARKERS = (
    "douyin.com",
    "tiktok.com",
    "bilibili.com",
    "b23.tv",
)
SOURCE_URL_PATTERN = re.compile(r"https?://[A-Za-z0-9._~:/?#@\[\]!$&'()*+,;=%-]+")


class ToolError(RuntimeError):
    """Expected operational error shown cleanly to CLI/API callers."""


_REPO_ROOT: Path | None = None
_HYBRID_CRAWLER: Any = None
_TIKTOK_DOWNLOAD_COOKIE_HEADERS: dict[str, str] = {}


def json_print(data: Any) -> None:
    print(json.dumps(data, ensure_ascii=False, indent=2, default=str))


def find_repo_root(repo_arg: str | None = None) -> Path:
    candidates: list[Path] = []

    if repo_arg:
        candidates.append(Path(repo_arg))

    env_repo = os.environ.get("DOUYIN_TIKTOK_API_REPO")
    if env_repo:
        candidates.append(Path(env_repo))

    script_path = Path(__file__).resolve()
    cwd = Path.cwd()
    candidates.extend(
        [
            cwd,
            cwd / "Douyin_TikTok_Download_API",
            cwd / "work" / "Douyin_TikTok_Download_API",
            script_path.parent / "Douyin_TikTok_Download_API",
            script_path.parent.parent / "Douyin_TikTok_Download_API",
            script_path.parent.parent / "work" / "Douyin_TikTok_Download_API",
        ]
    )

    for candidate in candidates:
        root = candidate.resolve()
        if (root / "crawlers" / "hybrid" / "hybrid_crawler.py").exists():
            return root

    searched = "\n  ".join(str(path) for path in candidates)
    raise ToolError(
        "Cannot find Douyin_TikTok_Download_API source tree. "
        "Pass --repo or set DOUYIN_TIKTOK_API_REPO.\nSearched:\n  " + searched
    )


def setup_repo(repo_arg: str | None = None) -> Path:
    global _REPO_ROOT
    if _REPO_ROOT is not None:
        return _REPO_ROOT

    repo_root = find_repo_root(repo_arg)
    repo_str = str(repo_root)
    if repo_str not in sys.path:
        sys.path.insert(0, repo_str)

    _REPO_ROOT = repo_root
    return repo_root


def configured_proxy() -> str | None:
    return (
        os.environ.get("TK_REFERENCE_DOWNLOAD_PROXY")
        or os.environ.get("HTTPS_PROXY")
        or os.environ.get("https_proxy")
        or os.environ.get("HTTP_PROXY")
        or os.environ.get("http_proxy")
    )


def parse_timeout_seconds() -> int:
    raw = os.environ.get("TK_REFERENCE_PARSE_TIMEOUT_SECONDS")
    try:
        return max(10, int(raw)) if raw else DEFAULT_PARSE_TIMEOUT_SECONDS
    except ValueError:
        return DEFAULT_PARSE_TIMEOUT_SECONDS


def ytdlp_timeout_seconds() -> int:
    raw = os.environ.get("TK_REFERENCE_YTDLP_TIMEOUT_SECONDS")
    try:
        return max(30, int(raw)) if raw else DEFAULT_YTDLP_TIMEOUT_SECONDS
    except ValueError:
        return DEFAULT_YTDLP_TIMEOUT_SECONDS


def douyin_cookie_timeout_seconds() -> int:
    raw = os.environ.get("TK_REFERENCE_DOUYIN_COOKIE_TIMEOUT_SECONDS")
    try:
        return max(10, int(raw)) if raw else DEFAULT_DOUYIN_COOKIE_TIMEOUT_SECONDS
    except ValueError:
        return DEFAULT_DOUYIN_COOKIE_TIMEOUT_SECONDS


def proxy_map(proxy: str | None) -> dict[str, str | None]:
    return {"http": proxy, "https": proxy, "http://": proxy, "https://": proxy}


def normalize_source_url(value: str) -> str:
    text = (value or "").strip()
    if not text:
        return text
    for match in SOURCE_URL_PATTERN.finditer(text):
        candidate = strip_trailing_url_punctuation(match.group(0))
        if is_supported_source_url(candidate):
            return candidate
    return text


def strip_trailing_url_punctuation(url: str) -> str:
    return (url or "").rstrip(".,;:!?)]}）】》\"'")


def is_supported_source_url(url: str) -> bool:
    parsed = urlparse(url)
    host = (parsed.netloc or "").lower()
    path = (parsed.path or "").lower()
    if any(marker in host for marker in SUPPORTED_SOURCE_HOST_MARKERS):
        return True
    return path.endswith((".mp4", ".mov", ".webm", ".m4v"))


def httpx_proxy_kwargs(proxy: str | None) -> dict[str, Any]:
    if not proxy:
        return {}
    return {"proxies": {"http://": proxy, "https://": proxy}}


def cookie_header_from_jar(cookies: Any) -> str | None:
    pairs: list[str] = []
    jar = getattr(cookies, "jar", cookies)
    for cookie in jar:
        name = getattr(cookie, "name", None)
        value = getattr(cookie, "value", None)
        if name and value:
            pairs.append(f"{name}={value}")
    return "; ".join(pairs) if pairs else None


def cookie_header_from_browser_cookies(cookies: list[dict[str, Any]]) -> str | None:
    pairs: list[str] = []
    for cookie in cookies:
        name = cookie.get("name")
        value = cookie.get("value")
        if name and value:
            pairs.append(f"{name}={value}")
    return "; ".join(pairs) if pairs else None


def is_douyin_url(url: str) -> bool:
    return "douyin.com" in (url or "").lower()


def apply_douyin_cookie_header(cookie_header: str) -> None:
    if not cookie_header:
        raise ToolError("Chromium 未生成可用抖音 Cookie")
    for module_name in ("crawlers.douyin.web.web_crawler", "crawlers.douyin.web.utils"):
        try:
            module = importlib.import_module(module_name)
        except ModuleNotFoundError:
            continue
        config_obj = getattr(module, "config", None)
        if isinstance(config_obj, dict):
            config_obj["TokenManager"]["douyin"]["headers"]["Cookie"] = cookie_header
            token_manager = getattr(module, "TokenManager", None)
            if token_manager is not None and hasattr(token_manager, "douyin_manager"):
                token_manager.douyin_manager = config_obj["TokenManager"]["douyin"]


def apply_proxy_to_config(config: dict[str, Any], provider: str, proxy: str | None) -> None:
    if not proxy:
        return
    provider_config = config.get("TokenManager", {}).get(provider, {})
    proxies = provider_config.setdefault("proxies", {})
    proxies["http"] = proxy
    proxies["https"] = proxy


def apply_runtime_proxy(proxy: str | None) -> None:
    if not proxy:
        return

    modules = [
        ("crawlers.douyin.web.web_crawler", "douyin"),
        ("crawlers.douyin.web.utils", "douyin"),
        ("crawlers.tiktok.web.web_crawler", "tiktok"),
        ("crawlers.tiktok.web.utils", "tiktok"),
        ("crawlers.tiktok.app.app_crawler", "tiktok"),
        ("crawlers.bilibili.web.web_crawler", "bilibili"),
    ]
    for module_name, provider in modules:
        try:
            module = importlib.import_module(module_name)
        except ModuleNotFoundError:
            continue
        config = getattr(module, "config", None)
        if isinstance(config, dict):
            apply_proxy_to_config(config, provider, proxy)
        token_manager = getattr(module, "TokenManager", None)
        if token_manager is not None and hasattr(token_manager, "proxies"):
            token_manager.proxies = {"http://": proxy, "https://": proxy}


def import_or_raise(module_name: str, attr_name: str | None = None) -> Any:
    try:
        module = importlib.import_module(module_name)
    except ModuleNotFoundError as exc:
        missing = exc.name or module_name
        repo = _REPO_ROOT or "<repo>"
        raise ToolError(
            f"Missing dependency or upstream module: {missing}. "
            f"Run: python -m pip install -r \"{repo}/requirements.txt\""
        ) from exc

    return getattr(module, attr_name) if attr_name else module


def get_hybrid_crawler() -> Any:
    global _HYBRID_CRAWLER
    if _HYBRID_CRAWLER is None:
        HybridCrawler = import_or_raise("crawlers.hybrid.hybrid_crawler", "HybridCrawler")
        apply_runtime_proxy(configured_proxy())
        _HYBRID_CRAWLER = HybridCrawler()
    return _HYBRID_CRAWLER


def first_present(data: dict[str, Any], keys: list[str]) -> Any:
    for key in keys:
        value = data.get(key)
        if value not in (None, "", [], {}):
            return value
    return None


def nested_first(data: dict[str, Any], paths: list[list[str]]) -> Any:
    for path in paths:
        current: Any = data
        for key in path:
            if isinstance(current, dict):
                current = current.get(key)
            elif isinstance(current, list) and isinstance(key, int) and 0 <= key < len(current):
                current = current[key]
            else:
                current = None
                break
        if current not in (None, "", [], {}):
            return current
    return None


def normalize_author(platform: str, author: Any) -> dict[str, Any] | Any:
    if not isinstance(author, dict):
        return author

    if platform == "bilibili":
        return {
            "id": author.get("mid"),
            "name": author.get("name"),
            "avatar": author.get("face"),
            "raw": author,
        }

    return {
        "id": first_present(author, ["uid", "id", "user_id", "sec_uid", "secUid"]),
        "sec_uid": first_present(author, ["sec_uid", "secUid"]),
        "unique_id": first_present(author, ["unique_id", "uniqueId", "short_id"]),
        "nickname": first_present(author, ["nickname", "name"]),
        "signature": author.get("signature"),
        "avatar": nested_first(
            author,
            [
                ["avatar_thumb", "url_list", 0],
                ["avatar_medium", "url_list", 0],
                ["avatar_larger", "url_list", 0],
            ],
        ),
        "raw": author,
    }


def normalize_parse_data(source_url: str, data: dict[str, Any], include_raw: bool = False) -> dict[str, Any]:
    platform = data.get("platform")
    item_type = data.get("type")

    media: dict[str, Any] = {"type": item_type}
    if item_type == "video":
        video_data = data.get("video_data") or {}
        media.update(
            {
                "watermark_url": video_data.get("wm_video_url"),
                "watermark_url_hq": video_data.get("wm_video_url_HQ"),
                "no_watermark_url": video_data.get("nwm_video_url"),
                "no_watermark_url_hq": video_data.get("nwm_video_url_HQ"),
                "audio_url": video_data.get("audio_url"),
                "cid": video_data.get("cid"),
            }
        )
    elif item_type == "image":
        image_data = data.get("image_data") or {}
        media.update(
            {
                "no_watermark_images": image_data.get("no_watermark_image_list") or [],
                "watermark_images": image_data.get("watermark_image_list") or [],
            }
        )

    result = {
        "source_url": source_url,
        "platform": platform,
        "type": item_type,
        "video_id": data.get("video_id"),
        "desc": data.get("desc"),
        "create_time": data.get("create_time"),
        "author": normalize_author(platform, data.get("author")),
        "statistics": data.get("statistics"),
        "hashtags": data.get("hashtags"),
        "cover": data.get("cover_data") or {},
        "media": media,
    }

    if include_raw:
        result["raw"] = data

    return result


async def parse_one_url_once(url: str, include_raw: bool = False) -> dict[str, Any]:
    crawler = get_hybrid_crawler()
    data = await asyncio.wait_for(
        crawler.hybrid_parsing_single_video(url=url, minimal=True),
        timeout=parse_timeout_seconds(),
    )
    if data is None:
        raise ToolError("获取数据失败")
    return normalize_parse_data(url, data, include_raw=include_raw)


async def retry_parse_douyin_with_fresh_cookie(
    url: str,
    include_raw: bool,
    original_error: Exception,
) -> dict[str, Any]:
    try:
        refresh_douyin_cookies(url)
    except Exception as cookie_error:
        raise ToolError(f"抖音解析失败：{original_error}；fresh cookies 刷新失败：{cookie_error}") from cookie_error
    try:
        return await parse_one_url_once(url, include_raw=include_raw)
    except Exception as retry_error:
        raise ToolError(f"抖音解析失败：{original_error}；fresh cookies 重试失败：{retry_error}") from retry_error


async def parse_one_url(url: str, include_raw: bool = False) -> dict[str, Any]:
    url = normalize_source_url(url)
    try:
        return await parse_one_url_once(url, include_raw=include_raw)
    except asyncio.TimeoutError as exc:
        if "tiktok.com" in url.lower():
            return await parse_tiktok_web_page(url, include_raw=include_raw)
        if is_douyin_url(url):
            return await retry_parse_douyin_with_fresh_cookie(url, include_raw, exc)
        raise ToolError(
            "Platform parsing timed out. Check that the URL is a real public video "
            "and that TK_REFERENCE_DOWNLOAD_PROXY can access TikTok/Douyin."
        ) from exc
    except Exception as exc:
        if "tiktok.com" in url.lower():
            return await parse_tiktok_web_page(url, include_raw=include_raw)
        if is_douyin_url(url):
            return await retry_parse_douyin_with_fresh_cookie(url, include_raw, exc)
        raise


async def fetch_tiktok_web_item(url: str) -> dict[str, Any]:
    httpx = import_or_raise("httpx")
    headers = {
        "User-Agent": DEFAULT_USER_AGENT,
        "Referer": "https://www.tiktok.com/",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    }
    kwargs: dict[str, Any] = {
        "timeout": parse_timeout_seconds(),
        "follow_redirects": True,
        "trust_env": False,
        **httpx_proxy_kwargs(configured_proxy()),
    }
    async with httpx.AsyncClient(**kwargs) as client:
        response = await client.get(url, headers=headers)
        response.raise_for_status()
        cookie_header = cookie_header_from_jar(client.cookies)
        if cookie_header:
            _TIKTOK_DOWNLOAD_COOKIE_HEADERS[url] = cookie_header
    html = response.text

    script_patterns = [
        r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">(.*?)</script>',
        r'<script id="SIGI_STATE" type="application/json">(.*?)</script>',
    ]
    for pattern in script_patterns:
        match = re.search(pattern, html, re.S)
        if not match:
            continue
        payload = html_lib.unescape(match.group(1))
        data = json.loads(payload)
        item = nested_first(
            data,
            [
                ["__DEFAULT_SCOPE__", "webapp.video-detail", "itemInfo", "itemStruct"],
                ["ItemModule", tiktok_video_id_from_url(url)],
            ],
        )
        if isinstance(item, dict):
            return item

    raise ToolError("Cannot find TikTok web video data in page HTML")


def tiktok_video_id_from_url(url: str) -> str | None:
    match = re.search(r"/(?:video|photo)/(\d+)", url)
    return match.group(1) if match else None


def first_url(value: Any) -> str | None:
    if isinstance(value, str) and value.startswith("http"):
        return value
    if isinstance(value, list):
        for item in value:
            url = first_url(item)
            if url:
                return url
    if isinstance(value, dict):
        for key in ["UrlList", "urlList", "url_list"]:
            url = first_url(value.get(key))
            if url:
                return url
    return None


def tiktok_web_video_urls(video: dict[str, Any]) -> list[str]:
    urls: list[str] = []

    def add(value: Any) -> None:
        if isinstance(value, str) and value.startswith("http") and value not in urls:
            urls.append(value)
        elif isinstance(value, list):
            for item in value:
                add(item)
        elif isinstance(value, dict):
            for key in ["UrlList", "urlList", "url_list"]:
                add(value.get(key))

    bitrate_info = video.get("bitrateInfo") or video.get("bitrate_info") or []
    if isinstance(bitrate_info, list) and bitrate_info:
        ranked = sorted(
            [item for item in bitrate_info if isinstance(item, dict)],
            key=lambda item: int(nested_first(item, [["PlayAddr", "DataSize"], ["playAddr", "dataSize"]]) or 0),
            reverse=True,
        )
        for item in ranked:
            add(item.get("PlayAddr") or item.get("playAddr"))
    add(video.get("playAddr"))
    add(video.get("downloadAddr"))
    return urls


def best_tiktok_web_video_url(video: dict[str, Any]) -> str | None:
    urls = tiktok_web_video_urls(video)
    return urls[0] if urls else None


def normalize_tiktok_web_item(source_url: str, item: dict[str, Any], include_raw: bool = False) -> dict[str, Any]:
    video = item.get("video") or {}
    video_url = best_tiktok_web_video_url(video)
    if not video_url:
        raise ToolError("Cannot find TikTok video play URL in web page data")

    cover = {
        "cover": video.get("cover"),
        "origin_cover": video.get("originCover"),
        "dynamic_cover": video.get("dynamicCover"),
    }
    author = item.get("author") or {}
    result = {
        "source_url": source_url,
        "platform": "tiktok",
        "type": "video",
        "video_id": item.get("id") or tiktok_video_id_from_url(source_url),
        "desc": item.get("desc"),
        "create_time": item.get("createTime"),
        "author": {
            "id": author.get("id"),
            "sec_uid": author.get("secUid"),
            "unique_id": author.get("uniqueId"),
            "nickname": author.get("nickname"),
            "signature": author.get("signature"),
            "avatar": author.get("avatarThumb") or author.get("avatarMedium"),
            "raw": author,
        },
        "statistics": item.get("stats") or item.get("statsV2"),
        "hashtags": item.get("textExtra") or [],
        "cover": cover,
        "media": {
            "type": "video",
            "watermark_url": video.get("downloadAddr"),
            "watermark_url_hq": video.get("downloadAddr"),
            "no_watermark_url": video_url,
            "no_watermark_url_hq": video_url,
            "candidate_urls": tiktok_web_video_urls(video),
            "audio_url": None,
            "cid": video.get("id"),
        },
    }
    if include_raw:
        result["raw"] = item
    return result


async def parse_tiktok_web_page(url: str, include_raw: bool = False) -> dict[str, Any]:
    item = await fetch_tiktok_web_item(url)
    return normalize_tiktok_web_item(url, item, include_raw=include_raw)


async def warm_tiktok_download_cookies(url: str) -> None:
    if _TIKTOK_DOWNLOAD_COOKIE_HEADERS.get(url):
        return
    try:
        await fetch_tiktok_web_item(url)
    except Exception:
        return


def parse_params_json_or_query(params_arg: str | None, endpoint: str | None = None) -> dict[str, Any]:
    if params_arg:
        try:
            parsed = json.loads(params_arg)
        except json.JSONDecodeError as exc:
            raise ToolError("--params must be a JSON object") from exc
        if not isinstance(parsed, dict):
            raise ToolError("--params must be a JSON object")
        return parsed

    if endpoint and "?" in endpoint:
        return dict(parse_qsl(urlparse(endpoint).query, keep_blank_values=True))

    return {}


def replace_query(url: str, params: dict[str, Any]) -> str:
    parsed = urlparse(url)
    query = urlencode(params)
    return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, query, parsed.fragment))


async def make_signature(
    platform: str,
    kind: str,
    endpoint: str | None = None,
    params: dict[str, Any] | None = None,
    user_agent: str = DEFAULT_USER_AGENT,
) -> dict[str, Any]:
    platform = platform.lower()
    normalized_kind = kind.lower().replace("-", "_")
    params = dict(params or {})

    if platform == "douyin":
        utils = import_or_raise("crawlers.douyin.web.utils")

        if normalized_kind in {"ms_token", "mstoken"}:
            token = utils.TokenManager.gen_real_msToken()
            return {"platform": platform, "kind": "msToken", "msToken": token}

        if normalized_kind in {"x_bogus", "xbogus"}:
            if not endpoint:
                raise ToolError("endpoint is required for X-Bogus")
            signed_url = utils.BogusManager.xb_str_2_endpoint(endpoint, user_agent)
            return {
                "platform": platform,
                "kind": "X-Bogus",
                "url": signed_url,
                "X-Bogus": signed_url.split("&X-Bogus=")[-1],
                "user_agent": user_agent,
            }

        if normalized_kind in {"a_bogus", "abogus", "a-bogus"}:
            if not params:
                params = parse_params_json_or_query(None, endpoint)
            if not params:
                raise ToolError("endpoint with query string or --params JSON is required for a_bogus")
            params["msToken"] = params.get("msToken", "")
            a_bogus = utils.BogusManager.ab_model_2_endpoint(params, user_agent)
            signed_url = None
            if endpoint:
                signed_url = replace_query(endpoint, params) + f"&a_bogus={a_bogus}"
            return {
                "platform": platform,
                "kind": "a_bogus",
                "a_bogus": a_bogus,
                "url": signed_url,
                "user_agent": user_agent,
            }

    if platform == "tiktok":
        utils = import_or_raise("crawlers.tiktok.web.utils")

        if normalized_kind in {"ms_token", "mstoken"}:
            token = utils.TokenManager.gen_real_msToken()
            return {"platform": platform, "kind": "msToken", "msToken": token}

        if normalized_kind in {"x_bogus", "xbogus"}:
            if not endpoint:
                raise ToolError("endpoint is required for X-Bogus")
            signed_url = utils.BogusManager.xb_str_2_endpoint(user_agent, endpoint)
            return {
                "platform": platform,
                "kind": "X-Bogus",
                "url": signed_url,
                "X-Bogus": signed_url.split("&X-Bogus=")[-1],
                "user_agent": user_agent,
            }

    if platform == "bilibili":
        if normalized_kind not in {"w_rid", "wrid"}:
            raise ToolError("Bilibili supports only w_rid")
        utils = import_or_raise("crawlers.bilibili.web.utils")
        if not params:
            params = parse_params_json_or_query(None, endpoint)
        if not params:
            raise ToolError("endpoint with query string or --params JSON is required for Bilibili w_rid")
        params.setdefault("wts", str(round(time.time())))
        signed_query = await utils.WridManager.wrid_model_endpoint(params)
        signed_url = None
        if endpoint:
            parsed = urlparse(endpoint)
            signed_url = urlunparse(
                (parsed.scheme, parsed.netloc, parsed.path, parsed.params, signed_query, parsed.fragment)
            )
        return {
            "platform": platform,
            "kind": "w_rid",
            "query": signed_query,
            "url": signed_url,
            "params": dict(parse_qsl(signed_query, keep_blank_values=True)),
        }

    raise ToolError(f"Unsupported platform/kind: {platform}/{kind}")


def safe_name(value: Any, fallback: str = "media") -> str:
    text = str(value or fallback)
    text = re.sub(r'[<>:"/\\|?*\x00-\x1f]+', "_", text)
    text = text.strip(" ._")
    return text[:120] or fallback


async def get_platform_headers(platform: str) -> dict[str, str]:
    crawler = get_hybrid_crawler()
    if platform == "tiktok":
        bundle = await crawler.TikTokWebCrawler.get_tiktok_headers()
    elif platform == "bilibili":
        bundle = await crawler.BilibiliWebCrawler.get_bilibili_headers()
    else:
        bundle = await crawler.DouyinWebCrawler.get_douyin_headers()
    return bundle.get("headers", bundle)


async def download_stream(
    url: str,
    file_path: Path,
    headers: dict[str, str] | None = None,
    require_video: bool = False,
) -> None:
    if not url:
        raise ToolError("Empty download URL")
    file_path.parent.mkdir(parents=True, exist_ok=True)
    httpx = import_or_raise("httpx")
    kwargs: dict[str, Any] = {"timeout": None, "follow_redirects": True, "trust_env": False}
    proxy = configured_proxy()
    if proxy:
        kwargs["proxies"] = {"http://": proxy, "https://": proxy}
    async with httpx.AsyncClient(**kwargs) as client:
        async with client.stream("GET", url, headers=headers or {}) as response:
            response.raise_for_status()
            content_type = response.headers.get("content-type")
            if require_video and is_explicit_non_video_content_type(content_type):
                raise ToolError(f"Non-video response from {urlparse(url).netloc}: {content_type}")
            with file_path.open("wb") as output:
                async for chunk in response.aiter_bytes():
                    if chunk:
                        output.write(chunk)
    if require_video:
        validate_downloaded_video_file(file_path, content_type=content_type)


async def download_first_available(urls: list[str], file_path: Path, headers: dict[str, str]) -> None:
    errors: list[str] = []
    for url in urls:
        try:
            await download_stream(url, file_path, headers=headers, require_video=True)
            return
        except Exception as exc:
            if file_path.exists():
                file_path.unlink()
            errors.append(f"{urlparse(url).netloc}: {exc}")
    raise ToolError("All candidate video URLs failed: " + " | ".join(errors[-3:]))


def tiktok_web_download_headers(source_url: str) -> dict[str, str]:
    headers = {
        "User-Agent": DEFAULT_USER_AGENT,
        "Referer": source_url,
        "Origin": "https://www.tiktok.com",
        "Accept": "video/webm,video/mp4,video/*;q=0.9,*/*;q=0.8",
        "Range": "bytes=0-",
    }
    cookie_header = _TIKTOK_DOWNLOAD_COOKIE_HEADERS.get(source_url)
    if cookie_header:
        headers["Cookie"] = cookie_header
    return headers


def is_explicit_non_video_content_type(content_type: str | None) -> bool:
    normalized = (content_type or "").split(";", 1)[0].strip().lower()
    if not normalized:
        return False
    if normalized.startswith("video/"):
        return False
    return normalized in {
        "text/html",
        "text/plain",
        "application/json",
        "application/xml",
        "text/xml",
    }


def has_video_container_signature(file_path: Path) -> bool:
    try:
        head = file_path.read_bytes()[:VIDEO_SIGNATURE_BYTES]
    except OSError:
        return False
    if not head:
        return False
    if b"ftyp" in head[:64]:
        return True
    return len(head) >= 4 and head[:4] == b"\x1a\x45\xdf\xa3"


def looks_like_risk_or_html_page(file_path: Path) -> bool:
    try:
        head = file_path.read_bytes()[:VIDEO_SIGNATURE_BYTES]
    except OSError:
        return False
    text = head.decode("utf-8", errors="ignore").strip().lower()
    return (
        text.startswith("<!doctype html")
        or text.startswith("<html")
        or "<head" in text
        or "captcha" in text
        or "verify" in text
        or "challenge" in text
    )


def validate_downloaded_video_file(file_path: Path, content_type: str | None = None) -> None:
    if is_explicit_non_video_content_type(content_type):
        raise ToolError(f"Downloaded response is not video content: {content_type}")
    if not file_path.exists() or file_path.stat().st_size <= 0:
        raise ToolError("Downloaded video file is empty")
    if looks_like_risk_or_html_page(file_path):
        raise ToolError("TikTok returned a risk-control or HTML page instead of a real video")
    if not has_video_container_signature(file_path):
        raise ToolError("Downloaded file is not a recognized MP4/WebM video container")


async def merge_video_audio(video_url: str, audio_url: str, output_path: Path, headers: dict[str, str]) -> None:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        raise ToolError("ffmpeg is required to merge Bilibili video/audio streams")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="bilibili_merge_") as temp_dir:
        temp = Path(temp_dir)
        video_file = temp / "video.m4v"
        audio_file = temp / "audio.m4a"
        await download_stream(video_url, video_file, headers=headers)
        await download_stream(audio_url, audio_file, headers=headers)

        cmd = [
            ffmpeg,
            "-y",
            "-loglevel",
            "error",
            "-i",
            str(video_file),
            "-i",
            str(audio_file),
            "-c:v",
            "copy",
            "-c:a",
            "copy",
            "-f",
            "mp4",
            str(output_path),
        ]
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            raise ToolError(result.stderr.strip() or "ffmpeg merge failed")


async def download_media(
    url: str,
    out_dir: Path,
    with_watermark: bool = False,
    prefix: str = "",
) -> dict[str, Any]:
    url = normalize_source_url(url)
    try:
        structured = await parse_one_url(url)
    except Exception as parse_error:
        if should_try_ytdlp_fallback(url):
            return download_with_ytdlp(
                url,
                out_dir=out_dir,
                with_watermark=with_watermark,
                prefix=prefix,
                parse_error=parse_error,
            )
        raise

    platform = structured.get("platform")
    item_type = structured.get("type")
    video_id = safe_name(structured.get("video_id"), "item")
    media = structured.get("media") or {}
    target_dir = out_dir / f"{platform}_{item_type}"
    file_prefix = safe_name(prefix, "") + ("_" if prefix else "")

    if item_type == "video":
        file_name = f"{file_prefix}{platform}_{video_id}"
        if with_watermark:
            file_name += "_watermark"
        output_path = target_dir / f"{file_name}.mp4"
        if output_path.exists():
            validate_downloaded_video_file(output_path)
            return {"path": str(output_path), "cached": True, "data": structured}

        headers = await get_platform_headers(platform)
        video_url = first_present(
            media,
            ["watermark_url_hq", "watermark_url"] if with_watermark
            else ["no_watermark_url_hq", "no_watermark_url", "watermark_url_hq", "watermark_url"],
        )

        if platform == "bilibili":
            audio_url = media.get("audio_url")
            if not video_url:
                raise ToolError("Cannot find Bilibili video URL in parsed data")
            if audio_url:
                await merge_video_audio(video_url, audio_url, output_path, headers)
            else:
                await download_stream(video_url, output_path, headers=headers)
        else:
            if not video_url:
                raise ToolError("Cannot find video URL in parsed data")
            candidate_urls = media.get("candidate_urls") or [video_url]
            if platform == "tiktok" and candidate_urls:
                await warm_tiktok_download_cookies(url)
                await download_first_available(candidate_urls, output_path, tiktok_web_download_headers(url))
            else:
                await download_stream(video_url, output_path, headers=headers)

        return {"path": str(output_path), "cached": False, "data": structured}

    if item_type == "image":
        suffix = "_images_watermark.zip" if with_watermark else "_images.zip"
        zip_path = target_dir / f"{file_prefix}{platform}_{video_id}{suffix}"
        if zip_path.exists():
            return {"path": str(zip_path), "cached": True, "data": structured}

        urls = media.get("watermark_images" if with_watermark else "no_watermark_images") or []
        if not urls:
            raise ToolError("Cannot find image URLs in parsed data")

        target_dir.mkdir(parents=True, exist_ok=True)
        image_paths: list[Path] = []
        for index, image_url in enumerate(urls, start=1):
            ext = Path(urlparse(image_url).path).suffix
            if not ext or len(ext) > 8:
                ext = ".jpg"
            image_path = target_dir / f"{file_prefix}{platform}_{video_id}_{index}{ext}"
            await download_stream(image_url, image_path)
            image_paths.append(image_path)

        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zip_file:
            for image_path in image_paths:
                zip_file.write(image_path, arcname=image_path.name)

        return {"path": str(zip_path), "cached": False, "data": structured}

    raise ToolError(f"Unsupported parsed media type: {item_type}")


def should_try_ytdlp_fallback(url: str) -> bool:
    url = normalize_source_url(url)
    lower = (url or "").lower()
    return "douyin.com" in lower or "tiktok.com" in lower


def platform_from_url(url: str) -> str:
    url = normalize_source_url(url)
    lower = (url or "").lower()
    if "douyin.com" in lower:
        return "douyin"
    if "tiktok.com" in lower:
        return "tiktok"
    return "external"


def ytdlp_base_command() -> list[str]:
    configured = os.environ.get("TK_REFERENCE_YTDLP_BIN")
    if configured:
        return [configured]
    executable = shutil.which("yt-dlp")
    if executable:
        return [executable]
    if importlib.util.find_spec("yt_dlp") is not None:
        return [sys.executable, "-m", "yt_dlp"]
    raise ToolError("备用下载器 yt-dlp 未安装，请安装 yt-dlp 后重试")


def build_ytdlp_command(url: str, output_template: Path, cookies_file: Path | None = None) -> list[str]:
    command = ytdlp_base_command()
    proxy = configured_proxy()
    if proxy:
        command.extend(["--proxy", proxy])
    if cookies_file is not None:
        command.extend(["--cookies", str(cookies_file)])
    command.extend([
        "--no-playlist",
        "--no-warnings",
        "--retries",
        "3",
        "--fragment-retries",
        "3",
        "--socket-timeout",
        "30",
        "--user-agent",
        DEFAULT_USER_AGENT,
        "-f",
        "bv*+ba/best",
        "--merge-output-format",
        "mp4",
        "-o",
        str(output_template),
        url,
    ])
    return command


def ytdlp_failed_for_douyin_fresh_cookies(output: str) -> bool:
    lowered = (output or "").lower()
    return any(marker in lowered for marker in DOUYIN_FRESH_COOKIE_MARKERS)


def download_with_ytdlp(
    url: str,
    out_dir: Path,
    with_watermark: bool = False,
    prefix: str = "",
    parse_error: Exception | None = None,
) -> dict[str, Any]:
    url = normalize_source_url(url)
    platform = platform_from_url(url)
    target_dir = out_dir / f"{platform}_video"
    target_dir.mkdir(parents=True, exist_ok=True)
    file_prefix = safe_name(prefix, "") + ("_" if prefix else "")
    output_template = target_dir / f"{file_prefix}ytdlp_%(extractor_key)s_%(id)s.%(ext)s"
    command = build_ytdlp_command(url, output_template)

    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=ytdlp_timeout_seconds())
    except subprocess.TimeoutExpired as exc:
        detail = f"yt-dlp 下载超时：{ytdlp_timeout_seconds()}s"
        if parse_error is not None:
            detail += f"；主解析失败：{parse_error}"
        raise ToolError(detail) from exc

    if result.returncode != 0:
        detail = (result.stderr or result.stdout or "").strip()
        if platform == "douyin" and ytdlp_failed_for_douyin_fresh_cookies(detail):
            cookies_file = refresh_douyin_cookies(url)
            retry_command = build_ytdlp_command(url, output_template, cookies_file=cookies_file)
            try:
                result = subprocess.run(
                    retry_command,
                    capture_output=True,
                    text=True,
                    timeout=ytdlp_timeout_seconds(),
                )
            except subprocess.TimeoutExpired as exc:
                retry_detail = f"yt-dlp 带抖音 fresh cookies 下载超时：{ytdlp_timeout_seconds()}s"
                if parse_error is not None:
                    retry_detail += f"；主解析失败：{parse_error}"
                raise ToolError(retry_detail) from exc
            if result.returncode == 0:
                detail = ""
            else:
                retry_detail = (result.stderr or result.stdout or "").strip()
                detail = f"{detail}；抖音 fresh cookies 重试失败：{retry_detail or result.returncode}"

        if result.returncode == 0:
            pass
        else:
            if parse_error is not None:
                detail = f"主解析失败：{parse_error}；yt-dlp 备用下载失败：{detail or result.returncode}"
            raise ToolError(detail or "yt-dlp 备用下载失败")

    downloaded = latest_ytdlp_video(target_dir, file_prefix)
    validate_downloaded_video_file(downloaded)
    return {
        "path": str(downloaded),
        "cached": False,
        "engine": "yt-dlp",
        "data": {
            "platform": platform,
            "type": "video",
            "video_id": downloaded.stem,
            "media": {
                "no_watermark_url": url,
                "watermark_url": url if with_watermark else None,
            },
            "source": {
                "url": url,
                "parse_error": str(parse_error) if parse_error is not None else None,
            },
        },
    }


def chromium_executable() -> str:
    configured = os.environ.get("TK_REFERENCE_CHROMIUM_BIN")
    if configured:
        return configured
    for name in ("chromium", "chromium-browser", "google-chrome", "google-chrome-stable"):
        executable = shutil.which(name)
        if executable:
            return executable
    for path in ("/usr/lib/chromium/chromium", "/usr/bin/chromium", "/usr/bin/google-chrome"):
        if Path(path).exists():
            return path
    raise ToolError("抖音 fresh cookies 需要 Chromium，请设置 TK_REFERENCE_CHROMIUM_BIN")


def douyin_cookies_file() -> Path:
    configured = os.environ.get("TK_REFERENCE_DOUYIN_COOKIES_FILE")
    if configured:
        return Path(configured)
    return Path(__file__).resolve().parent / ".cache" / "douyin-cookies.txt"


def refresh_douyin_cookies(url: str) -> Path:
    cookies_file = douyin_cookies_file()
    cookies_file.parent.mkdir(parents=True, exist_ok=True)
    cookies = fetch_douyin_browser_cookies(url)
    cookie_header = cookie_header_from_browser_cookies(cookies)
    if cookie_header:
        apply_douyin_cookie_header(cookie_header)
    write_netscape_cookies(cookies_file, cookies)
    return cookies_file


def fetch_douyin_browser_cookies(url: str) -> list[dict[str, Any]]:
    timeout = douyin_cookie_timeout_seconds()
    port = free_local_port()
    user_data_dir = Path(tempfile.mkdtemp(prefix="tk-douyin-cookies-"))
    command = [
        chromium_executable(),
        "--headless",
        "--no-sandbox",
        "--disable-gpu",
        "--disable-dev-shm-usage",
        "--disable-background-networking",
        "--disable-sync",
        "--disable-extensions",
        "--disable-default-apps",
        "--disable-blink-features=AutomationControlled",
        "--user-agent=" + DEFAULT_USER_AGENT,
        f"--remote-debugging-port={port}",
        f"--user-data-dir={user_data_dir}",
        "about:blank",
    ]
    proxy = configured_proxy()
    if proxy:
        command.insert(1, f"--proxy-server={proxy}")

    process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        deadline = time.time() + timeout
        ws_url = wait_for_cdp_websocket(port, deadline)
        cdp = CdpWebSocket(ws_url)
        try:
            cdp.call("Page.enable")
            cdp.call("Network.enable")
            cdp.call("Page.navigate", {"url": url})
            wait_for_douyin_cookies(cdp, deadline)
            cookies = cdp.call("Network.getAllCookies").get("cookies") or []
        finally:
            cdp.close()
    finally:
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
        shutil.rmtree(user_data_dir, ignore_errors=True)

    douyin_cookies = [
        cookie for cookie in cookies
        if "douyin.com" in (cookie.get("domain") or "")
    ]
    if not any(cookie.get("name") == "s_v_web_id" for cookie in douyin_cookies):
        raise ToolError("Chromium 未生成抖音 fresh cookies：缺少 s_v_web_id")
    return douyin_cookies


def wait_for_douyin_cookies(cdp: "CdpWebSocket", deadline: float) -> None:
    while time.time() < deadline:
        cookies = cdp.call("Network.getAllCookies").get("cookies") or []
        if any(
            cookie.get("name") == "s_v_web_id"
            and "douyin.com" in (cookie.get("domain") or "")
            for cookie in cookies
        ):
            return
        time.sleep(1)
    raise ToolError("Chromium 生成抖音 fresh cookies 超时")


def free_local_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def wait_for_cdp_websocket(port: int, deadline: float) -> str:
    url = f"http://127.0.0.1:{port}/json/list"
    last_error: Exception | None = None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    while time.time() < deadline:
        try:
            with opener.open(url, timeout=2) as response:
                data = json.loads(response.read().decode("utf-8"))
            targets = data if isinstance(data, list) else []
            for target in targets:
                if target.get("type") == "page" and target.get("webSocketDebuggerUrl"):
                    return target["webSocketDebuggerUrl"]
        except (OSError, urllib.error.URLError, json.JSONDecodeError) as exc:
            last_error = exc
        time.sleep(0.5)
    raise ToolError(f"Chromium DevTools 启动超时：{last_error}")


def write_netscape_cookies(path: Path, cookies: list[dict[str, Any]]) -> None:
    lines = ["# Netscape HTTP Cookie File"]
    for cookie in cookies:
        name = cookie.get("name")
        value = cookie.get("value")
        domain = cookie.get("domain") or ".douyin.com"
        if not name or value is None:
            continue
        include_subdomains = "TRUE" if str(domain).startswith(".") else "FALSE"
        path_value = cookie.get("path") or "/"
        secure = "TRUE" if cookie.get("secure") else "FALSE"
        expires = cookie.get("expires")
        try:
            expires_value = str(max(0, int(float(expires))))
        except (TypeError, ValueError):
            expires_value = "0"
        lines.append("\t".join([
            str(domain),
            include_subdomains,
            str(path_value),
            secure,
            expires_value,
            str(name),
            str(value),
        ]))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


class CdpWebSocket:
    def __init__(self, ws_url: str):
        parsed = urlparse(ws_url)
        if parsed.scheme != "ws":
            raise ToolError(f"Unsupported DevTools websocket URL: {ws_url}")
        self.socket = socket.create_connection((parsed.hostname or "127.0.0.1", parsed.port or 80), timeout=5)
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        path = urlunparse(("", "", parsed.path or "/", "", parsed.query, ""))
        request = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {parsed.netloc}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n"
        )
        self.socket.sendall(request.encode("ascii"))
        response = self.socket.recv(4096)
        if b" 101 " not in response.split(b"\r\n", 1)[0]:
            raise ToolError("Chromium DevTools websocket 握手失败")
        self.next_id = 0

    def call(self, method: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        self.next_id += 1
        message = {"id": self.next_id, "method": method}
        if params is not None:
            message["params"] = params
        self._send_json(message)
        while True:
            payload = self._recv_json()
            if payload.get("id") == self.next_id:
                if payload.get("error"):
                    raise ToolError(f"Chromium DevTools 调用失败：{payload['error']}")
                return payload.get("result") or {}

    def close(self) -> None:
        try:
            self.socket.close()
        except OSError:
            pass

    def _send_json(self, payload: dict[str, Any]) -> None:
        data = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        header = bytearray([0x81])
        length = len(data)
        if length < 126:
            header.append(0x80 | length)
        elif length < 65536:
            header.extend([0x80 | 126, (length >> 8) & 0xFF, length & 0xFF])
        else:
            header.append(0x80 | 127)
            header.extend(length.to_bytes(8, "big"))
        mask = os.urandom(4)
        masked = bytes(byte ^ mask[index % 4] for index, byte in enumerate(data))
        self.socket.sendall(bytes(header) + mask + masked)

    def _recv_json(self) -> dict[str, Any]:
        first = self._recv_exact(2)
        opcode = first[0] & 0x0F
        if opcode == 0x8:
            raise ToolError("Chromium DevTools websocket 已关闭")
        length = first[1] & 0x7F
        if length == 126:
            length = int.from_bytes(self._recv_exact(2), "big")
        elif length == 127:
            length = int.from_bytes(self._recv_exact(8), "big")
        masked = bool(first[1] & 0x80)
        mask = self._recv_exact(4) if masked else b""
        data = self._recv_exact(length)
        if masked:
            data = bytes(byte ^ mask[index % 4] for index, byte in enumerate(data))
        return json.loads(data.decode("utf-8"))

    def _recv_exact(self, length: int) -> bytes:
        chunks = bytearray()
        while len(chunks) < length:
            chunk = self.socket.recv(length - len(chunks))
            if not chunk:
                raise ToolError("Chromium DevTools websocket 读取失败")
            chunks.extend(chunk)
        return bytes(chunks)


def latest_ytdlp_video(target_dir: Path, file_prefix: str) -> Path:
    candidates = [
        path
        for path in target_dir.glob(f"{file_prefix}ytdlp_*")
        if path.is_file()
        and path.suffix.lower() in {".mp4", ".mov", ".webm", ".m4v", ".mkv"}
        and not path.name.endswith(".part")
    ]
    if not candidates:
        raise ToolError("yt-dlp 未生成可用视频文件")
    return max(candidates, key=lambda path: path.stat().st_mtime)


def create_app(repo: str | None, download_dir: Path):
    setup_repo(repo)

    try:
        fastapi = import_or_raise("fastapi")
        responses = import_or_raise("starlette.responses")
    except ToolError:
        raise

    app = fastapi.FastAPI(title="Single Link Douyin/TikTok/Bilibili Tool")

    @app.get("/api/health")
    async def health():
        return {"ok": True, "repo": str(_REPO_ROOT)}

    @app.get("/api/parse")
    async def api_parse(url: str, include_raw: bool = False):
        try:
            return await parse_one_url(url, include_raw=include_raw)
        except Exception as exc:
            raise fastapi.HTTPException(status_code=400, detail=str(exc))

    @app.get("/api/signature")
    async def api_signature(
        platform: str,
        kind: str,
        endpoint: str | None = None,
        params_json: str | None = None,
        user_agent: str = DEFAULT_USER_AGENT,
    ):
        try:
            params = parse_params_json_or_query(params_json, endpoint)
            return await make_signature(platform, kind, endpoint, params, user_agent)
        except Exception as exc:
            raise fastapi.HTTPException(status_code=400, detail=str(exc))

    @app.get("/api/download")
    async def api_download(
        url: str,
        with_watermark: bool = False,
        prefix: bool = False,
    ):
        try:
            result = await download_media(
                url=url,
                out_dir=download_dir,
                with_watermark=with_watermark,
                prefix="download" if prefix else "",
            )
            path = Path(result["path"])
            media_type = "application/zip" if path.suffix.lower() == ".zip" else "video/mp4"
            return responses.FileResponse(str(path), media_type=media_type, filename=path.name)
        except Exception as exc:
            raise fastapi.HTTPException(status_code=400, detail=str(exc))

    return app


async def command_parse(args: argparse.Namespace) -> None:
    setup_repo(args.repo)
    result = await parse_one_url(args.url, include_raw=args.include_raw)
    json_print(result)


async def command_signature(args: argparse.Namespace) -> None:
    setup_repo(args.repo)
    params = parse_params_json_or_query(args.params, args.endpoint)
    result = await make_signature(args.platform, args.kind, args.endpoint, params, args.user_agent)
    json_print(result)


async def command_download(args: argparse.Namespace) -> None:
    setup_repo(args.repo)
    result = await download_media(
        url=args.url,
        out_dir=Path(args.out_dir).resolve(),
        with_watermark=args.with_watermark,
        prefix=args.prefix,
    )
    json_print(result)


def command_serve(args: argparse.Namespace) -> None:
    download_dir = Path(args.out_dir).resolve()
    app = create_app(args.repo, download_dir=download_dir)
    uvicorn = import_or_raise("uvicorn")
    uvicorn.run(app, host=args.host, port=args.port)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Douyin/TikTok/Bilibili single-link parser/downloader")
    parser.add_argument(
        "--repo",
        help="Path to Evil0ctal/Douyin_TikTok_Download_API source tree. "
        "Default: auto-detect or DOUYIN_TIKTOK_API_REPO.",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    parse_cmd = subparsers.add_parser("parse", help="Parse one URL and print structured JSON")
    parse_cmd.add_argument("url")
    parse_cmd.add_argument("--include-raw", action="store_true", help="Include upstream minimal raw payload")

    sig_cmd = subparsers.add_parser("signature", help="Generate request signature/token parameters")
    sig_cmd.add_argument("--platform", required=True, choices=["douyin", "tiktok", "bilibili"])
    sig_cmd.add_argument(
        "--kind",
        required=True,
        help="douyin: X-Bogus|a_bogus|msToken; tiktok: X-Bogus|msToken; bilibili: w_rid",
    )
    sig_cmd.add_argument("--endpoint", help="Full endpoint or query string source")
    sig_cmd.add_argument("--params", help='JSON object, e.g. {"aid":"6383","aweme_id":"..."}')
    sig_cmd.add_argument("--user-agent", default=DEFAULT_USER_AGENT)

    dl_cmd = subparsers.add_parser("download", help="Download one parsed video/image item")
    dl_cmd.add_argument("url")
    dl_cmd.add_argument("--out-dir", default="downloads")
    dl_cmd.add_argument("--with-watermark", action="store_true")
    dl_cmd.add_argument("--prefix", default="")

    serve_cmd = subparsers.add_parser("serve", help="Start FastAPI service")
    serve_cmd.add_argument("--host", default="127.0.0.1")
    serve_cmd.add_argument("--port", type=int, default=8000)
    serve_cmd.add_argument("--out-dir", default="downloads")

    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()

    try:
        if args.command == "parse":
            asyncio.run(command_parse(args))
        elif args.command == "signature":
            asyncio.run(command_signature(args))
        elif args.command == "download":
            asyncio.run(command_download(args))
        elif args.command == "serve":
            command_serve(args)
        else:
            parser.error(f"Unknown command: {args.command}")
    except ToolError as exc:
        print(f"error: {exc}", file=sys.stderr)
        raise SystemExit(2) from exc
    except KeyboardInterrupt:
        raise SystemExit(130)


if __name__ == "__main__":
    main()
