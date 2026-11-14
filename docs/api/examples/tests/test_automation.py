"""Local transport tests: never calls a real platform or spends credits."""
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

EXAMPLES = Path(__file__).resolve().parents[1]


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def respond(self, data):
        payload = json.dumps(data).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_POST(self):
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        self.server.calls.append(('POST', self.path, dict(self.headers), body))
        if '/refresh-token?' in self.path:
            if self.server.reject_refresh:
                return self.respond({'code': 401, 'msg': 'refresh expired'})
            return self.respond({'code': 0, 'data': {'accessToken': 'renewed', 'refreshToken': 'next-refresh'}})
        if self.headers.get('Authorization') == 'Bearer expired':
            return self.respond({'code': 401, 'msg': 'expired'})
        if self.server.error:
            return self.respond({'code': 1060000029, 'msg': 'insufficient credits'})
        if self.server.drop_create:
            self.connection.close()
            return
        return self.respond({'code': 0, 'data': {'taskId': 42, 'status': 'PENDING'}})

    def do_GET(self):
        self.server.calls.append(('GET', self.path, dict(self.headers), b''))
        if self.path.startswith('/download'):
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'video-bytes')
            return
        if self.headers.get('Authorization') == 'Bearer expired':
            return self.respond({'code': 401, 'msg': 'expired'})
        if '/material-libraries' in self.path:
            data = {'list': [{'id': 1, 'name': '芒果干'}], 'total': 1}
        elif '/voices' in self.path:
            data = [{'voiceCode': 'gentle', 'name': '温柔学姐', 'ttsProvider': 'MINIMAX'}]
        elif '/bgms' in self.path:
            data = [{'id': 2, 'name': '温馨居家01', 'fileName': '温馨居家01.mp3'}]
        elif '/credits' in self.path:
            data = {'remainingCredits': 100}
        elif '/download?' in self.path:
            data = {'taskId': 42, 'fileName': 'video.mp4', 'downloadUrl': self.server.base + '/download-file'}
        else:
            data = {'taskId': 42, 'status': 'PENDING' if self.server.pending else 'SUCCESS',
                    'completed': not self.server.pending, 'failed': False}
        return self.respond({'code': 0, 'data': data})


class ExampleTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.server.base = 'http://127.0.0.1:' + str(self.server.server_port)
        self.server.calls = []
        self.server.error = False
        self.server.reject_refresh = False
        self.server.drop_create = False
        self.server.pending = False
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.request = self.directory / 'request.json'
        self.request.write_text(json.dumps({'libraryId': 1, 'scriptText': '完整口播正文', 'ttsProvider': 'MINIMAX',
                                            'voiceCode': 'gentle', 'bgmAssetId': 2}), encoding='utf-8')

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.temp.cleanup()

    def execute(self, runtime, *args, access='good'):
        command = [sys.executable, str(EXAMPLES / 'automation.py')] if runtime == 'python' else ['node', str(EXAMPLES / 'automation.mjs')]
        environment = dict(os.environ, PYTHONUTF8='1', BASE_URL=self.server.base, TENANT_ID='7', TK_ACCESS_TOKEN=access,
                           TK_REFRESH_TOKEN='refresh', TK_TOKEN_FILE=str(self.directory / 'session.json'))
        return subprocess.run(command + list(args), cwd=self.directory, env=environment, text=True,
                              capture_output=True, timeout=12, encoding='utf-8')

    def create(self, runtime, *extra, access='good'):
        return self.execute(runtime, 'create', '--request', str(self.request), '--state', 'task.json',
                            '--output', 'video.mp4', '--poll-interval', '0.01', '--timeout', '1', *extra, access=access)

    def test_create_persists_id_polls_and_downloads_without_forwarding_credentials(self):
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                (self.directory / 'task.json').unlink(missing_ok=True)
                (self.directory / 'video.mp4').unlink(missing_ok=True)
                result = self.create(runtime)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads((self.directory / 'task.json').read_text())['taskId'], 42)
                self.assertEqual((self.directory / 'video.mp4').read_bytes(), b'video-bytes')
                download = [call for call in self.server.calls if call[1] == '/download-file'][-1]
                self.assertNotIn('Authorization', download[2])
                self.assertNotIn('tenant-id', {name.lower(): value for name, value in download[2].items()})

    def test_duplicate_create_does_not_submit_twice(self):
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                (self.directory / 'task.json').write_text('{"taskId":42}')
                result = self.create(runtime)
                self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.server.calls)

    def test_failed_create_is_not_retried_and_keeps_uncertain_submission_record(self):
        self.server.error = True
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                (self.directory / 'task.json').unlink(missing_ok=True)
                before = len([call for call in self.server.calls if call[0] == 'POST'])
                result = self.create(runtime)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(len([call for call in self.server.calls if call[0] == 'POST']), before + 1)
                self.assertTrue((self.directory / 'task.json').is_file())

    def test_expired_token_refreshes_once_and_persists_updated_tokens(self):
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                result = self.execute(runtime, 'resources', access='expired')
                self.assertEqual(result.returncode, 0, result.stderr)
                tokens = json.loads((self.directory / 'session.json').read_text())
                self.assertEqual(tokens['refreshToken'], 'next-refresh')
                self.assertNotIn('next-refresh', result.stdout)

    def test_resume_never_submits_and_reports_poll_timeout(self):
        self.server.pending = True
        (self.directory / 'task.json').write_text('{"taskId":42}')
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                result = self.execute(runtime, 'resume', '--state', 'task.json', '--output', 'video.mp4',
                                      '--poll-interval', '0.01', '--timeout', '0.05')
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('resume', result.stderr)
        self.assertFalse([call for call in self.server.calls if call[0] == 'POST'])

    def test_lost_create_response_is_not_retried_and_keeps_submission_marker(self):
        self.server.drop_create = True
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                (self.directory / 'task.json').unlink(missing_ok=True)
                before = len([call for call in self.server.calls if call[0] == 'POST'])
                result = self.create(runtime)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(len([call for call in self.server.calls if call[0] == 'POST']), before + 1)
                marker = json.loads((self.directory / 'task.json').read_text())
                self.assertEqual(marker['status'], 'SUBMITTING')
                self.assertNotIn('taskId', marker)

    def test_refresh_failure_stops_instead_of_looping_or_submitting(self):
        self.server.reject_refresh = True
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                before = len([call for call in self.server.calls if '/refresh-token?' in call[1]])
                result = self.execute(runtime, 'resources', access='expired')
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(len([call for call in self.server.calls if '/refresh-token?' in call[1]]), before + 1)
        self.assertFalse([call for call in self.server.calls if call[1].endswith('/tasks')])

    def test_invalid_voice_and_provider_are_rejected_before_submission(self):
        request = json.loads(self.request.read_text())
        request['ttsProvider'] = 'MIMO'
        self.request.write_text(json.dumps(request))
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                result = self.create(runtime)
                self.assertNotEqual(result.returncode, 0)
        self.assertFalse([call for call in self.server.calls if call[0] == 'POST'])

    def test_dry_run_validates_without_network_or_submission(self):
        for runtime in ('python', 'node'):
            with self.subTest(runtime=runtime):
                result = self.create(runtime, '--dry-run')
                self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.server.calls)
        self.assertFalse((self.directory / 'task.json').exists())

    def test_nonfinite_or_nonpositive_poll_options_are_rejected_before_network(self):
        for runtime in ('python', 'node'):
            for option in ('--timeout', '--poll-interval'):
                for value in ('nan', 'inf', '1e309', '0', '-1'):
                    with self.subTest(runtime=runtime, option=option, value=value):
                        self.server.calls.clear()
                        result = self.execute(runtime, 'resources', option, value)
                        self.assertNotEqual(result.returncode, 0)
                        self.assertFalse(self.server.calls)


if __name__ == '__main__':
    unittest.main()
