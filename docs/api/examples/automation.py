#!/usr/bin/env python3
"""ClipForge account-token automation example. Python 3.10+, standard library only."""
import argparse
import json
import math
import os
import shutil
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener

PREFIX = '/admin-api/tk/automation/v1'


class ApiError(RuntimeError):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def safe_url(value):
    url = urlsplit(value)
    if url.username or url.password or url.fragment or not url.hostname:
        raise ApiError('地址必须是不含凭证或片段的 HTTP(S) URL')
    if url.scheme != 'https' and not (url.scheme == 'http' and url.hostname in ('localhost', '127.0.0.1', '::1')):
        raise ApiError('生产地址必须使用 HTTPS；HTTP 仅允许本机测试')
    return value


def write_json(path, data, exclusive=False):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if exclusive else os.O_TRUNC)
    with os.fdopen(os.open(path, flags, 0o600), 'w', encoding='utf-8') as target:
        json.dump(data, target, ensure_ascii=False, indent=2)


def validate_request(data):
    for field in ('libraryId', 'bgmAssetId'):
        if not isinstance(data.get(field), int) or isinstance(data[field], bool) or data[field] <= 0:
            raise ApiError(field + ' 必须是 resources 返回的正整数 ID')
    if not isinstance(data.get('scriptText'), str) or not data['scriptText'].strip() or not 1 <= len(data['scriptText']) <= 3000:
        raise ApiError('scriptText 必须是 1–3000 字的完整口播正文')
    if data.get('ttsProvider') not in ('MINIMAX', 'DASHSCOPE', 'MIMO'):
        raise ApiError('ttsProvider 无效')
    code, profile = data.get('voiceCode'), data.get('voiceProfileId')
    if bool(code and str(code).strip()) == (profile is not None):
        raise ApiError('voiceCode 与 voiceProfileId 必须二选一')
    if profile is not None and (not isinstance(profile, int) or isinstance(profile, bool) or profile <= 0):
        raise ApiError('voiceProfileId 必须是正整数')
    if code is not None and not isinstance(code, str):
        raise ApiError('voiceCode 必须是字符串')
    if data['ttsProvider'] == 'MIMO' and profile is None:
        raise ApiError('MIMO 必须选择 voiceProfileId')
    for key, expected in {'translationEnabled': False, 'targetRegion': 'CN', 'durationMode': 'FOLLOW_NARRATION',
                          'width': 1080, 'height': 1920, 'fps': 30, 'subtitleFontSize': 'extra_large',
                          'subtitleStyle': 'classic_white', 'subtitlePositionMode': 'smart_safe'}.items():
        if key in data and (data[key] != expected or type(data[key]) is not type(expected)):
            raise ApiError(key + ' 不支持该值')
    volume = data.get('bgmVolume', 0.4)
    if isinstance(volume, bool) or not isinstance(volume, (float, int)) or not 0 <= volume <= 1:
        raise ApiError('bgmVolume 必须在 0–1 之间')
    if 'title' in data and (not isinstance(data['title'], str) or len(data['title']) > 128):
        raise ApiError('title 必须是最多 128 字的字符串')
    for key in ('subtitleEnabled', 'subtitleKeywordEnabled', 'subtitleKaraokeEnabled'):
        if key in data and not isinstance(data[key], bool):
            raise ApiError(key + ' 必须是布尔值')
    return data


class Client:
    def __init__(self):
        self.base = safe_url(os.environ.get('BASE_URL', 'https://clipforgestudio.fnn.net.cn').rstrip('/'))
        if urlsplit(self.base).path not in ('', '/') or urlsplit(self.base).query:
            raise ApiError('BASE_URL 只填域名，不包含 /admin-api 或查询参数')
        self.tenant = os.environ.get('TENANT_ID', '')
        if not self.tenant.isdigit() or int(self.tenant) <= 0:
            raise ApiError('请设置自己的 TENANT_ID')
        self.token_file = Path(os.environ.get('TK_TOKEN_FILE') or str(Path.home() / '.clipforge/session.json'))
        saved = json.loads(self.token_file.read_text(encoding='utf-8')) if self.token_file.exists() else {}
        if saved and (saved.get('baseUrl') != self.base or saved.get('tenantId') != self.tenant):
            raise ApiError('Token 文件属于其他环境/租户，请指定独立 TK_TOKEN_FILE')
        self.access = os.environ.get('TK_ACCESS_TOKEN') or saved.get('accessToken')
        self.refresh = os.environ.get('TK_REFRESH_TOKEN') or saved.get('refreshToken')
        self.opener = build_opener(NoRedirect())

    def save_tokens(self, data):
        if not data.get('accessToken'):
            raise ApiError('鉴权响应缺少 accessToken')
        self.access = data['accessToken']
        self.refresh = data.get('refreshToken', self.refresh)
        write_json(self.token_file, {'baseUrl': self.base, 'tenantId': self.tenant, 'accessToken': self.access,
                                     'refreshToken': self.refresh, 'expiresTime': data.get('expiresTime')})

    def send(self, method, path, data=None, authenticated=True):
        headers = {'Content-Type': 'application/json', 'tenant-id': self.tenant}
        if authenticated and self.access:
            headers['Authorization'] = 'Bearer ' + self.access
        body = json.dumps(data, ensure_ascii=False).encode('utf-8') if data is not None else None
        request = Request(self.base + path, data=body, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=30) as response:
                status, raw = response.status, response.read()
        except HTTPError as error:
            status, raw = error.code, error.read()
        except (OSError, URLError):
            raise ApiError('网络请求失败；创建响应不明时不要重复 create，请核对生成记录') from None
        try:
            result = json.loads(raw)
        except (ValueError, TypeError):
            raise ApiError('服务未返回有效 JSON；创建响应不明时请核对生成记录') from None
        return status, result

    def login(self):
        if self.access:
            return
        if self.refresh:
            self.renew()
            return
        username, password = os.environ.get('TK_USERNAME'), os.environ.get('TK_PASSWORD')
        if not username or not password:
            raise ApiError('请配置 TK_ACCESS_TOKEN，或 TK_USERNAME / TK_PASSWORD（开启验证码时还需有效凭证）')
        body = {'username': username, 'password': password}
        if os.environ.get('TK_CAPTCHA_VERIFICATION'):
            body['captchaVerification'] = os.environ['TK_CAPTCHA_VERIFICATION']
        status, result = self.send('POST', '/admin-api/system/auth/login', body, authenticated=False)
        self.check(status, result)
        self.save_tokens(result['data'])

    @staticmethod
    def check(status, result):
        if status >= 400 or result.get('code') != 0:
            raise ApiError('接口失败：code=' + str(result.get('code', status)) + '；' + str(result.get('msg', '请检查权限和参数')))

    def renew(self):
        if not self.refresh:
            raise ApiError('Token 已过期且无 refreshToken，请重新登录')
        status, result = self.send('POST', '/admin-api/system/auth/refresh-token?' + urlencode({'refreshToken': self.refresh}), authenticated=False)
        self.check(status, result)
        self.save_tokens(result['data'])

    def request(self, method, path, data=None):
        status, result = self.send(method, path, data)
        if status == 401 or result.get('code') == 401:
            # An explicit authentication rejection has not accepted the generation task.
            self.renew()
            status, result = self.send(method, path, data)
        self.check(status, result)
        return result['data']

    def resources(self, library_name=None):
        libraries, page = [], 1
        while True:
            params = {'pageNo': page, 'pageSize': 100}
            if library_name:
                params['name'] = library_name
            batch = self.request('GET', PREFIX + '/material-libraries?' + urlencode(params))
            libraries.extend(batch['list'])
            if len(libraries) >= batch['total'] or not batch['list']:
                break
            page += 1
            if page > 100:
                raise ApiError('素材库超过示例查询范围，请按 --library-name 筛选')
        return {'credits': self.request('GET', PREFIX + '/credits'), 'materialLibraries': libraries,
                'voices': self.request('GET', PREFIX + '/voices'), 'bgms': self.request('GET', PREFIX + '/bgms')}

    def download(self, url, output):
        safe_url(url)
        target = Path(output)
        if target.suffix.lower() != '.mp4' or target.exists():
            raise ApiError('输出必须是尚不存在的 .mp4 文件')
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = Path(str(target) + '.part')
        try:
            # Separate request: no Authorization or tenant-id on a signed storage URL.
            with self.opener.open(Request(url), timeout=60) as response, partial.open('xb') as destination:
                shutil.copyfileobj(response, destination, length=1024 * 1024)
            # Exclusive final name: a concurrent writer must not get overwritten.
            os.link(partial, target)
            partial.unlink()
        except (OSError, URLError):
            raise ApiError('下载失败，已保留 .part 文件；检查后删除 .part 并 resume，勿重新 create') from None


def check_resources(request, resources):
    if not any(item['id'] == request['libraryId'] for item in resources['materialLibraries']):
        raise ApiError('该账号无法使用 libraryId，请先 resources 查询')
    voices = [voice for voice in resources['voices'] if voice.get('ttsProvider') == request['ttsProvider']
              and ((request.get('voiceProfileId') is not None and voice.get('voiceProfileId') == request['voiceProfileId'])
                   or (request.get('voiceCode') and voice.get('voiceCode') == request['voiceCode']))]
    if len(voices) != 1:
        raise ApiError('音色不可用、重复或与 ttsProvider 不匹配，请先 resources 查询')
    if not any(item['id'] == request['bgmAssetId'] for item in resources['bgms']):
        raise ApiError('bgmAssetId 不是当前账号可用的自有上传曲目')


def finish(client, state, output, timeout, interval):
    record = json.loads(Path(state).read_text(encoding='utf-8'))
    if record.get('baseUrl', client.base) != client.base or str(record.get('tenantId', client.tenant)) != client.tenant:
        raise ApiError('任务记录属于其他环境或租户')
    task_id = record.get('taskId')
    if not isinstance(task_id, int) or isinstance(task_id, bool) or task_id <= 0:
        raise ApiError('记录没有 taskId；请先在网页核对响应不明的提交，确认后填入 taskId，再 resume')
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        task = client.request('GET', PREFIX + '/tasks/' + str(task_id))
        if task.get('failed') is True or task.get('status') == 'FAILED':
            raise ApiError('任务失败：' + str(task.get('failCode', '')) + '；' + str(task.get('failReason', '')))
        if task.get('completed') is True and task.get('status') == 'SUCCESS':
            if task.get('subtitleKaraokeStatus') and task.get('subtitleKaraokeStatus') not in ('SUCCESS', 'READY'):
                print('字幕诊断：' + str(task['subtitleKaraokeStatus']) + '；' + str(task.get('subtitleKaraokeReason', '')))
            asset = client.request('GET', PREFIX + '/tasks/' + str(task_id) + '/download?' + urlencode({'fileName': Path(output).name}))
            client.download(asset['downloadUrl'], output)
            print('已保存视频：' + str(output))
            return
        time.sleep(min(interval, max(0, deadline - time.monotonic())))
    raise ApiError('等待超时；任务可能仍在运行，请保留记录并使用 resume 继续查询')


def main(argv=None):
    parser = argparse.ArgumentParser(description='ClipForge 出片 API 示例；create 会提交可能扣积分的生成任务。')
    parser.add_argument('command', choices=['resources', 'create', 'resume'])
    parser.add_argument('--request', default='request.json', help='只放口播正文及查询取得的资源 ID')
    parser.add_argument('--state', default='task.json', help='每次新任务使用不同文件，已有记录拒绝再次 create')
    parser.add_argument('--output', default='video.mp4')
    parser.add_argument('--library-name', help='resources 查询素材库名称，可省略')
    parser.add_argument('--timeout', type=float, default=900)
    parser.add_argument('--poll-interval', type=float, default=3)
    parser.add_argument('--dry-run', action='store_true', help='create 仅本地校验，不登录、不发请求、不扣积分')
    args = parser.parse_args(argv)
    if not all(math.isfinite(value) and value > 0 for value in (args.timeout, args.poll_interval)):
        raise ApiError('timeout 和 poll-interval 必须是大于 0 的有限数；生产建议轮询间隔 3–5 秒')
    if args.dry_run and args.command != 'create':
        raise ApiError('--dry-run 仅用于 create')
    if args.command == 'create':
        if Path(args.state).exists():
            raise ApiError('已有任务记录，拒绝重复提交；请使用 resume 或为新的任务指定新的 --state')
        request = validate_request(json.loads(Path(args.request).read_text(encoding='utf-8-sig')))
        if args.dry_run:
            print('本地校验通过；尚未验证资源权限，也未提交生成任务')
            return
    client = Client()
    client.login()
    if args.command == 'resources':
        print(json.dumps(client.resources(args.library_name), ensure_ascii=False, indent=2))
        return
    if Path(args.output).exists() or Path(args.output).suffix.lower() != '.mp4':
        raise ApiError('--output 必须是尚不存在的 .mp4 文件')
    if args.command == 'create':
        check_resources(request, client.resources())
        record = {'status': 'SUBMITTING', 'baseUrl': client.base, 'tenantId': client.tenant,
                  'title': request.get('title'), 'submittedAt': time.strftime('%Y-%m-%dT%H:%M:%S%z')}
        write_json(args.state, record, exclusive=True)
        created = client.request('POST', PREFIX + '/tasks', request)
        record.update({'taskId': created['taskId'], 'status': 'SUBMITTED'})
        write_json(args.state, record)
        print('已提交 taskId=' + str(created['taskId']) + '；任务记录=' + args.state)
    finish(client, args.state, args.output, args.timeout, args.poll_interval)


if __name__ == '__main__':
    try:
        main()
    except (ApiError, ValueError, OSError, KeyError) as error:
        # Never print request headers, passwords, refresh URLs or signed download URLs.
        print('错误：' + (str(error) if isinstance(error, ApiError) else '本地配置/文件或响应格式错误，请检查文件与参数'), file=sys.stderr)
        sys.exit(1)
