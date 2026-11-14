"""Documentation contracts: guard client authentication and advertised request shape."""
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = json.loads((ROOT / 'tk-platform-openapi.json').read_text(encoding='utf-8'))
PREFIX = '/admin-api/tk/automation/v1'


class ApiContractTest(unittest.TestCase):
    def test_all_automation_operations_exist_and_require_both_account_and_tenant(self):
        for suffix, method in [('/credits', 'get'), ('/material-libraries', 'get'),
                               ('/voices', 'get'), ('/bgms', 'get'), ('/tasks', 'post'),
                               ('/tasks/{taskId}', 'get'), ('/tasks/{taskId}/download', 'get')]:
            with self.subTest(suffix=suffix):
                operation = SPEC['paths'].get(PREFIX + suffix, {}).get(method)
                self.assertIsNotNone(operation, 'document every deployed automation endpoint')
                self.assertEqual(operation['security'], [{'AdminBearer': [], 'TenantId': []}])

    def test_hmac_headers_are_an_and_requirement_instead_of_alternatives(self):
        expected = {'TkClientId', 'TkTimestamp', 'TkNonce', 'TkSignature'}
        for path, item in SPEC['paths'].items():
            if '/tk/open/v1/tiktok/' not in path:
                continue
            for method, operation in item.items():
                if method not in ('get', 'post', 'put', 'delete'):
                    continue
                security = operation.get('security', SPEC['security'])
                if not security:
                    continue
                with self.subTest(path=path, method=method):
                    self.assertEqual(len(security), 1, 'multiple objects mean OR in OpenAPI')
                    self.assertTrue(expected.issubset(security[0]))

    def test_voice_selection_and_supported_defaults_match_validation(self):
        request = SPEC['components']['schemas'].get('AutomationCreateReq', {})
        self.assertEqual(set(request.get('required', [])), {'libraryId', 'scriptText', 'ttsProvider', 'bgmAssetId'})
        self.assertEqual(len(request.get('oneOf', [])), 2, 'voice ID and code are mutually exclusive')
        props = request['properties']
        self.assertEqual(props['scriptText']['maxLength'], 3000)
        for name, value in [('durationMode', 'FOLLOW_NARRATION'), ('width', 1080),
                            ('height', 1920), ('fps', 30), ('translationEnabled', False)]:
            self.assertEqual(props[name]['default'], value)
            self.assertEqual(props[name]['enum'], [value])
        self.assertEqual(props['bgmVolume']['minimum'], 0)
        self.assertEqual(props['bgmVolume']['maximum'], 1)

    def test_success_and_business_error_envelopes_cannot_overlap(self):
        self.assertEqual(SPEC['components']['schemas']['OpenApiResponse']['properties']['code']['example'], 0)
        error = SPEC['components']['schemas']['AccountApiError']
        self.assertEqual(error['properties']['code']['not'], {'enum': [0]})
        for path, item in SPEC['paths'].items():
            if not (path.startswith(PREFIX) or path.startswith('/admin-api/system/')):
                continue
            for method, operation in item.items():
                if method not in ('get', 'post'):
                    continue
                media = operation['responses']['200']['content']['application/json']
                self.assertEqual(media['schema']['oneOf'][0]['properties']['code']['enum'], [0])
                self.assertEqual(media['examples']['success']['value']['code'], 0)
                self.assertNotEqual(media['examples']['businessError']['value']['code'], 0)

    def test_public_tenant_lookup_does_not_inherit_hmac(self):
        operation = SPEC['paths']['/admin-api/system/tenant/get-id-by-name']['get']
        self.assertEqual(operation['security'], [])
        for suffix in ('/login', '/refresh-token'):
            operation = SPEC['paths']['/admin-api/system/auth' + suffix]['post']
            self.assertEqual(operation['security'], [{'TenantId': []}])

    def test_current_tiktok_public_routes_are_all_retained(self):
        latest = json.loads((ROOT.parent / 'openapi' / 'tiktok-open-api.json').read_text(encoding='utf-8'))
        for path, methods in latest['paths'].items():
            target = '/admin-api/tk/open/v1/tiktok' + path
            for method in methods:
                if method in ('get', 'post', 'put', 'delete'):
                    with self.subTest(path=path, method=method):
                        self.assertIn(method, SPEC['paths'].get(target, {}))

    def test_photo_contract_keeps_video_default_and_documents_async_conversion(self):
        schemas = SPEC['components']['schemas']
        upload = schemas['UploadCreateReq']
        self.assertEqual(upload['properties']['mediaType']['default'], 'VIDEO')
        photo_upload = upload['allOf'][0]['oneOf'][1]
        self.assertEqual(photo_upload['properties']['fileSize']['maximum'], 20 * 1024 * 1024)
        self.assertIn('PROCESSING', schemas['MediaResp']['properties']['status']['enum'])
        self.assertTrue(schemas['MediaResp']['properties']['mediaId']['nullable'])
        publish = schemas['TaskCreateReq']
        self.assertNotIn('mediaId', publish['required'])
        self.assertEqual(publish['properties']['photoMediaIds']['maxItems'], 35)
        self.assertEqual(publish['allOf'][0]['oneOf'][1]['properties']['title']['maxLength'], 90)
        self.assertIn('scheduledAt', publish['properties'], 'retain existing schedule fields during PHOTO merge')
        self.assertIn('scheduleEpochMillis', schemas['TaskResp']['properties'])
        self.assertIn('xOssObjectAcl', schemas['OssFields']['properties'])

    def test_local_references_resolve_and_existing_publish_api_is_preserved(self):
        self.assertIn('/admin-api/tk/open/v1/tiktok/publish/quick-tasks', SPEC['paths'])
        self.assertIn('/admin-api/tk/open/v1/tiktok/publish/tasks/{taskId}/metrics', SPEC['paths'])

        def walk(value):
            if isinstance(value, dict):
                if '$ref' in value and value['$ref'].startswith('#/'):
                    target = SPEC
                    for part in value['$ref'][2:].split('/'):
                        target = target[part.replace('~1', '/').replace('~0', '~')]
                for child in value.values():
                    walk(child)
            elif isinstance(value, list):
                for child in value:
                    walk(child)
        walk(SPEC)

    def test_postman_create_requires_explicit_paid_call_opt_in(self):
        collection = json.loads((ROOT / 'tk-automation.postman_collection.json').read_text(encoding='utf-8'))
        create = next(item for item in collection['item'] if item['name'].startswith('07'))
        prerequest = next(event for event in create['event'] if event['listen'] == 'prerequest')
        script = '\n'.join(prerequest['script']['exec'])
        self.assertIn('allowPaidCreate', script)
        self.assertIn('skipRequest', script)
        body = json.loads(create['request']['body']['raw'].replace('{{libraryId}}', '1').replace('{{bgmAssetId}}', '1'))
        self.assertIn('scriptText', body)

    def test_distributed_postman_environment_has_no_credentials_and_disables_create(self):
        environment = json.loads((ROOT / 'tk-automation.postman_environment.json').read_text(encoding='utf-8'))
        values = {item['key']: item['value'] for item in environment['values']}
        self.assertEqual(values['allowPaidCreate'], 'false')
        for key in ('username', 'password', 'accessToken', 'refreshToken', 'captchaVerification', 'downloadUrl'):
            with self.subTest(key=key):
                self.assertEqual(values[key], '')


if __name__ == '__main__':
    unittest.main()
