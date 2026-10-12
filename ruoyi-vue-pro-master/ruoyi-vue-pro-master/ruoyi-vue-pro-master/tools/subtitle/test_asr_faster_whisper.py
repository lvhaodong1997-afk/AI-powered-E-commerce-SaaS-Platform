import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "asr_faster_whisper", Path(__file__).with_name("asr_faster_whisper.py")
)
asr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(asr)


class SubtitleLanguageTest(unittest.TestCase):
    def test_country_locales_select_the_matching_asr_language(self):
        locales = {
            "zh-cn": "zh", "en-us": "en", "en-gb": "en", "ja-jp": "ja",
            "ko-kr": "ko", "de-de": "de", "fr-fr": "fr", "es-es": "es",
            "it-it": "it", "pt-br": "pt", "es-mx": "es", "nl-nl": "nl",
            "tr-tr": "tr", "pt-pt": "pt", "vi-vn": "vi", "th-th": "th",
        }
        for locale, language in locales.items():
            with self.subTest(locale=locale):
                self.assertEqual(language, asr.whisper_language(locale))
                self.assertEqual(language, asr.whisper_language(locale.upper().replace("-", "_")))

    def test_legacy_codes_and_unknown_languages_keep_auto_detection(self):
        self.assertEqual("de", asr.whisper_language("de"))
        self.assertEqual("en", asr.whisper_language("en"))
        for value in (None, "", "unsupported", "english"):
            with self.subTest(value=value):
                self.assertIsNone(asr.whisper_language(value))

    def test_asr_preserves_unspaced_scripts_and_passes_base_language(self):
        cases = (
            ("ja-jp", ["今日", "届く"], "今日届く", "ja"),
            ("th-th", ["พร้อม", "ส่ง"], "พร้อมส่ง", "th"),
            ("en-gb", ["ready", "today"], "ready today", "en"),
        )
        for locale, tokens, expected_text, expected_language in cases:
            with self.subTest(locale=locale):
                calls = []

                class FakeWhisperModel:
                    def __init__(self, *args, **kwargs):
                        pass

                    def transcribe(self, audio, **kwargs):
                        calls.append(kwargs)
                        words = [SimpleNamespace(word=token, start=i, end=i + 1)
                                 for i, token in enumerate(tokens)]
                        return [SimpleNamespace(words=words, text="", start=0, end=2)], SimpleNamespace(duration=2)

                args = SimpleNamespace(model="small", audio="unused.wav", language=locale, text=expected_text)
                with patch.dict("sys.modules", {"faster_whisper": SimpleNamespace(WhisperModel=FakeWhisperModel)}):
                    result = asr.faster_whisper_timeline(args, [])
                self.assertEqual(expected_language, calls[0]["language"])
                self.assertEqual(expected_text, result["segments"][0]["text"])
                self.assertEqual(locale, result["language"])


if __name__ == "__main__":
    unittest.main()
