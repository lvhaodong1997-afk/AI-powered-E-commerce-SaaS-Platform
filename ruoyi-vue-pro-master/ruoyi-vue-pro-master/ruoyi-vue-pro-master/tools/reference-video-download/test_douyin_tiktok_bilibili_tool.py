import asyncio
import os
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import douyin_tiktok_bilibili_tool as tool


class CandidateVideoDownloadTest(unittest.TestCase):

    def test_normalizes_douyin_share_text_to_short_url(self):
        share_text = (
            "9.43 12/18 s@e.OX :6pm tEU:/ 梁博《日落大道》他们说主办方把老天爷请来了哈哈哈哈哈哈哈哈 "
            "所有人感受这个金黄耀眼的日落大道！配上绝美灯光！人生时刻+1！"
            "https://v.douyin.com/NYZ_4Fw-o30/ 复制此链接，打开Dou音搜索，直接观看视频！"
        )

        self.assertEqual(
            "https://v.douyin.com/NYZ_4Fw-o30/",
            tool.normalize_source_url(share_text),
        )

    def test_download_media_passes_normalized_share_url_to_parser(self):
        share_text = "复制文案 https://v.douyin.com/NYZ_4Fw-o30/ 复制此链接，打开Dou音搜索"
        normalized_url = "https://v.douyin.com/NYZ_4Fw-o30/"
        with tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "douyin_video" / "douyin_123.mp4"
            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_bytes(b"\x00\x00\x00\x18ftypisom")
            original_parse_one_url = tool.parse_one_url
            original_get_platform_headers = tool.get_platform_headers

            async def fake_parse_one_url(url):
                self.assertEqual(normalized_url, url)
                return {
                    "platform": "douyin",
                    "type": "video",
                    "video_id": "123",
                    "media": {"no_watermark_url": output_path.as_uri()},
                }

            async def fake_get_platform_headers(platform):
                return {}

            try:
                tool.parse_one_url = fake_parse_one_url
                tool.get_platform_headers = fake_get_platform_headers

                result = asyncio.run(tool.download_media(share_text, Path(temp_dir)))
            finally:
                tool.parse_one_url = original_parse_one_url
                tool.get_platform_headers = original_get_platform_headers

            self.assertEqual(str(output_path), result["path"])

    def test_skips_html_risk_page_and_keeps_trying_candidate_urls(self):
        with LocalCandidateServer() as server, tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "tiktok_video" / "tiktok_123.mp4"

            asyncio.run(tool.download_first_available([
                server.url("/risk"),
                server.url("/video"),
            ], output_path, headers={}))

            self.assertEqual(b"\x00\x00\x00\x18ftypisom", output_path.read_bytes())

    def test_plain_stream_download_does_not_require_video_signature(self):
        with LocalCandidateServer() as server, tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "metadata.json"

            asyncio.run(tool.download_stream(server.url("/json"), output_path, headers={}))

            self.assertEqual(b'{"ok":true}', output_path.read_bytes())

    def test_tiktok_download_reuses_web_page_cookie(self):
        source_url = "https://www.tiktok.com/@demo/video/123"
        with LocalCandidateServer() as server, tempfile.TemporaryDirectory() as temp_dir:
            output_path = Path(temp_dir) / "tiktok_video" / "tiktok_123.mp4"
            original_parse_one_url = tool.parse_one_url
            original_get_platform_headers = tool.get_platform_headers
            original_cookie_headers = dict(tool._TIKTOK_DOWNLOAD_COOKIE_HEADERS)

            async def fake_parse_one_url(url):
                tool._TIKTOK_DOWNLOAD_COOKIE_HEADERS[url] = "tt_chain_token=ok"
                return {
                    "platform": "tiktok",
                    "type": "video",
                    "video_id": "123",
                    "media": {
                        "no_watermark_url": server.url("/cookie-video"),
                        "candidate_urls": [server.url("/cookie-video")],
                    },
                }

            async def fake_get_platform_headers(platform):
                return {}

            try:
                tool.parse_one_url = fake_parse_one_url
                tool.get_platform_headers = fake_get_platform_headers

                result = asyncio.run(tool.download_media(source_url, Path(temp_dir)))
            finally:
                tool.parse_one_url = original_parse_one_url
                tool.get_platform_headers = original_get_platform_headers
                tool._TIKTOK_DOWNLOAD_COOKIE_HEADERS.clear()
                tool._TIKTOK_DOWNLOAD_COOKIE_HEADERS.update(original_cookie_headers)

            self.assertEqual(str(output_path), result["path"])
            self.assertEqual(b"\x00\x00\x00\x18ftypisom", output_path.read_bytes())

    def test_download_media_falls_back_to_ytdlp_when_platform_parse_fails(self):
        source_url = "https://v.douyin.com/OGkURN-2Hxs/"
        with tempfile.TemporaryDirectory() as temp_dir:
            fallback_path = Path(temp_dir) / "ytdlp_video" / "reference_ytdlp_douyin.mp4"
            fallback_path.parent.mkdir(parents=True, exist_ok=True)
            fallback_path.write_bytes(b"\x00\x00\x00\x18ftypisom")
            original_parse_one_url = tool.parse_one_url
            original_download_with_ytdlp = getattr(tool, "download_with_ytdlp", None)
            calls = []

            async def fake_parse_one_url(url):
                raise tool.ToolError("获取数据失败")

            def fake_download_with_ytdlp(url, out_dir, with_watermark=False, prefix="", parse_error=None):
                calls.append(parse_error)
                if parse_error is None:
                    raise tool.ToolError("优先下载失败")
                self.assertEqual(source_url, url)
                self.assertEqual(Path(temp_dir), out_dir)
                self.assertIn("获取数据失败", str(parse_error))
                self.assertIn("优先下载失败", str(parse_error))
                return {
                    "path": str(fallback_path),
                    "cached": False,
                    "engine": "yt-dlp",
                    "data": {
                        "platform": "douyin",
                        "type": "video",
                        "video_id": "ytdlp_douyin",
                    },
                }

            try:
                tool.parse_one_url = fake_parse_one_url
                tool.download_with_ytdlp = fake_download_with_ytdlp

                result = asyncio.run(tool.download_media(source_url, Path(temp_dir), prefix="reference"))
            finally:
                tool.parse_one_url = original_parse_one_url
                if original_download_with_ytdlp is None:
                    delattr(tool, "download_with_ytdlp")
                else:
                    tool.download_with_ytdlp = original_download_with_ytdlp

            self.assertEqual(str(fallback_path), result["path"])
            self.assertEqual("yt-dlp", result["engine"])
            self.assertEqual(2, len(calls))

    def test_douyin_download_uses_ytdlp_before_platform_parse(self):
        source_url = "https://www.douyin.com/video/123"
        with tempfile.TemporaryDirectory() as temp_dir:
            fallback_path = Path(temp_dir) / "douyin_video" / "reference_ytdlp_douyin.mp4"
            fallback_path.parent.mkdir(parents=True, exist_ok=True)
            fallback_path.write_bytes(b"\x00\x00\x00\x18ftypisom")
            original_parse_one_url = tool.parse_one_url
            original_download_with_ytdlp = tool.download_with_ytdlp
            calls = []

            async def fail_parse_one_url(url):
                raise AssertionError("Douyin video download should try yt-dlp before app08 parsing")

            def fake_download_with_ytdlp(url, out_dir, with_watermark=False, prefix="", parse_error=None):
                calls.append(("ytdlp", url, prefix, parse_error))
                return {
                    "path": str(fallback_path),
                    "cached": False,
                    "engine": "yt-dlp",
                    "data": {
                        "platform": "douyin",
                        "type": "video",
                        "video_id": "ytdlp_douyin",
                    },
                }

            try:
                tool.parse_one_url = fail_parse_one_url
                tool.download_with_ytdlp = fake_download_with_ytdlp

                result = asyncio.run(tool.download_media(source_url, Path(temp_dir), prefix="reference"))
            finally:
                tool.parse_one_url = original_parse_one_url
                tool.download_with_ytdlp = original_download_with_ytdlp

            self.assertEqual(str(fallback_path), result["path"])
            self.assertEqual("yt-dlp", result["engine"])
            self.assertEqual([("ytdlp", source_url, "reference", None)], calls)

    def test_douyin_parse_retries_after_refreshing_runtime_cookie(self):
        source_url = "https://www.douyin.com/video/123"
        original_get_hybrid_crawler = tool.get_hybrid_crawler
        original_refresh = tool.refresh_douyin_cookies
        calls = []

        class FakeCrawler:
            async def hybrid_parsing_single_video(self, url, minimal):
                calls.append(("parse", url, minimal))
                if len([call for call in calls if call[0] == "parse"]) == 1:
                    raise tool.ToolError("获取数据失败")
                return {
                    "platform": "douyin",
                    "type": "video",
                    "video_id": "123",
                    "video_data": {
                        "nwm_video_url": "https://example.com/video.mp4",
                    },
                }

        def fake_refresh(url):
            calls.append(("refresh", url))
            return Path("douyin-cookies.txt")

        try:
            tool.get_hybrid_crawler = lambda: FakeCrawler()
            tool.refresh_douyin_cookies = fake_refresh

            result = asyncio.run(tool.parse_one_url(source_url))
        finally:
            tool.get_hybrid_crawler = original_get_hybrid_crawler
            tool.refresh_douyin_cookies = original_refresh

        self.assertEqual("123", result["video_id"])
        self.assertEqual([
            ("parse", source_url, True),
            ("refresh", source_url),
            ("parse", source_url, True),
        ], calls)

    def test_tiktok_parse_failure_does_not_refresh_douyin_cookie(self):
        source_url = "https://www.tiktok.com/@demo/video/123"
        original_get_hybrid_crawler = tool.get_hybrid_crawler
        original_refresh = tool.refresh_douyin_cookies
        original_tiktok_fallback = tool.parse_tiktok_web_page

        class FakeCrawler:
            async def hybrid_parsing_single_video(self, url, minimal):
                raise tool.ToolError("TikTok API unavailable")

        async def fake_tiktok_fallback(url, include_raw=False):
            return {"source_url": url, "platform": "tiktok", "type": "video", "video_id": "123"}

        def fail_refresh(url):
            raise AssertionError("TikTok must not refresh Douyin cookies")

        try:
            tool.get_hybrid_crawler = lambda: FakeCrawler()
            tool.refresh_douyin_cookies = fail_refresh
            tool.parse_tiktok_web_page = fake_tiktok_fallback

            result = asyncio.run(tool.parse_one_url(source_url))
        finally:
            tool.get_hybrid_crawler = original_get_hybrid_crawler
            tool.refresh_douyin_cookies = original_refresh
            tool.parse_tiktok_web_page = original_tiktok_fallback

        self.assertEqual("tiktok", result["platform"])

    def test_ytdlp_proxy_argument_follows_python_module_command(self):
        source_url = "https://v.douyin.com/OGkURN-2Hxs/"
        proxy_url = "http://127.0.0.1:7890"
        with tempfile.TemporaryDirectory() as temp_dir:
            original_base_command = tool.ytdlp_base_command
            original_subprocess_run = tool.subprocess.run
            previous_proxy = os.environ.get("TK_REFERENCE_DOWNLOAD_PROXY")

            def fake_base_command():
                return [tool.sys.executable, "-m", "yt_dlp"]

            def fake_run(command, capture_output, text, timeout):
                target = Path(temp_dir) / "douyin_video" / "reference_ytdlp_Douyin_123.mp4"
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(b"\x00\x00\x00\x18ftypisom")

                class Result:
                    returncode = 0
                    stdout = ""
                    stderr = ""

                self.assertEqual(
                    [tool.sys.executable, "-m", "yt_dlp", "--proxy", proxy_url],
                    command[:5],
                )
                return Result()

            try:
                tool.ytdlp_base_command = fake_base_command
                tool.subprocess.run = fake_run
                os.environ["TK_REFERENCE_DOWNLOAD_PROXY"] = proxy_url

                result = tool.download_with_ytdlp(source_url, Path(temp_dir), prefix="reference")
            finally:
                tool.ytdlp_base_command = original_base_command
                tool.subprocess.run = original_subprocess_run
                if previous_proxy is None:
                    os.environ.pop("TK_REFERENCE_DOWNLOAD_PROXY", None)
                else:
                    os.environ["TK_REFERENCE_DOWNLOAD_PROXY"] = previous_proxy

            self.assertEqual("yt-dlp", result["engine"])

    def test_douyin_ytdlp_refreshes_cookies_when_fresh_cookie_required(self):
        source_url = "https://v.douyin.com/OGkURN-2Hxs/"
        with tempfile.TemporaryDirectory() as temp_dir:
            original_base_command = tool.ytdlp_base_command
            original_subprocess_run = tool.subprocess.run
            original_refresh = tool.refresh_douyin_cookies
            calls = []

            def fake_base_command():
                return [tool.sys.executable, "-m", "yt_dlp"]

            def fake_refresh(url):
                self.assertEqual(source_url, url)
                cookie_path = Path(temp_dir) / "douyin-cookies.txt"
                cookie_path.write_text("# Netscape HTTP Cookie File\n", encoding="utf-8")
                calls.append("refresh")
                return cookie_path

            def fake_run(command, capture_output, text, timeout):
                calls.append(command)

                class Result:
                    stdout = ""

                if len([item for item in calls if isinstance(item, list)]) == 1:
                    Result.returncode = 1
                    Result.stderr = "ERROR: [Douyin] 123: Fresh cookies (not necessarily logged in) are needed"
                    return Result()

                self.assertIn("--cookies", command)
                target = Path(temp_dir) / "douyin_video" / "reference_ytdlp_Douyin_123.mp4"
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(b"\x00\x00\x00\x18ftypisom")
                Result.returncode = 0
                Result.stderr = ""
                return Result()

            try:
                tool.ytdlp_base_command = fake_base_command
                tool.subprocess.run = fake_run
                tool.refresh_douyin_cookies = fake_refresh

                result = tool.download_with_ytdlp(source_url, Path(temp_dir), prefix="reference")
            finally:
                tool.ytdlp_base_command = original_base_command
                tool.subprocess.run = original_subprocess_run
                tool.refresh_douyin_cookies = original_refresh

            self.assertEqual("yt-dlp", result["engine"])
            self.assertEqual(2, len([item for item in calls if isinstance(item, list)]))
            self.assertEqual(["refresh"], [item for item in calls if isinstance(item, str)])

    def test_tiktok_ytdlp_does_not_refresh_douyin_cookies(self):
        source_url = "https://www.tiktok.com/@demo/video/123"
        with tempfile.TemporaryDirectory() as temp_dir:
            original_subprocess_run = tool.subprocess.run
            original_refresh = tool.refresh_douyin_cookies
            original_base_command = tool.ytdlp_base_command

            def fake_base_command():
                return [tool.sys.executable, "-m", "yt_dlp"]

            def fail_refresh(url):
                raise AssertionError("TikTok must not refresh Douyin cookies")

            def fake_run(command, capture_output, text, timeout):
                class Result:
                    returncode = 1
                    stdout = ""
                    stderr = "ERROR: [TikTok] unavailable"
                return Result()

            try:
                tool.ytdlp_base_command = fake_base_command
                tool.subprocess.run = fake_run
                tool.refresh_douyin_cookies = fail_refresh

                with self.assertRaises(tool.ToolError):
                    tool.download_with_ytdlp(source_url, Path(temp_dir), prefix="reference")
            finally:
                tool.subprocess.run = original_subprocess_run
                tool.refresh_douyin_cookies = original_refresh
                tool.ytdlp_base_command = original_base_command


class LocalCandidateServer:

    def __enter__(self):
        self.previous_proxy_env = {
            key: os.environ.pop(key, None)
            for key in ("HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy", "TK_REFERENCE_DOWNLOAD_PROXY")
        }
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), CandidateHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        return self

    def __exit__(self, exc_type, exc, tb):
        self.server.shutdown()
        self.thread.join(timeout=5)
        self.server.server_close()
        for key, value in self.previous_proxy_env.items():
            if value is not None:
                os.environ[key] = value

    def url(self, path):
        return f"http://127.0.0.1:{self.server.server_port}{path}"


class CandidateHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path == "/video":
            self.send_response(200)
            self.send_header("Content-Type", "video/mp4")
            self.end_headers()
            self.wfile.write(b"\x00\x00\x00\x18ftypisom")
            return

        if self.path == "/json":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"ok":true}')
            return

        if self.path == "/cookie-video":
            if "tt_chain_token=ok" not in self.headers.get("Cookie", ""):
                self.send_response(403)
                self.end_headers()
                return
            self.send_response(200)
            self.send_header("Content-Type", "video/mp4")
            self.end_headers()
            self.wfile.write(b"\x00\x00\x00\x18ftypisom")
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"<!doctype html><html><head><title>Verify</title></head></html>")

    def log_message(self, format, *args):
        return


if __name__ == "__main__":
    unittest.main()
