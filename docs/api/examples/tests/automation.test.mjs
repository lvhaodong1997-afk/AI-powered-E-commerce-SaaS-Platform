import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError, safeUrl, validateRequest } from '../automation.mjs';

const valid = () => ({ libraryId: 1, scriptText: '完整口播正文', ttsProvider: 'MINIMAX', voiceCode: 'gentle', bgmAssetId: 2 });

test('voice selection must be exactly one identifier and MiMo requires a profile', () => {
  assert.throws(() => validateRequest({ ...valid(), voiceProfileId: 3 }), ApiError);
  assert.throws(() => validateRequest({ ...valid(), ttsProvider: 'MIMO' }), ApiError);
  const request = { ...valid(), ttsProvider: 'MIMO', voiceProfileId: 3 };
  delete request.voiceCode;
  assert.equal(validateRequest(request).voiceProfileId, 3);
});

test('reject invalid output contract and nonfinite volume before spending credits', () => {
  for (const changed of [{ bgmVolume: NaN }, { bgmVolume: 2 }, { translationEnabled: true }, { width: 720 },
    { subtitleEnabled: null }, { scriptText: 'x'.repeat(3001) }, { libraryId: '1' }]) {
    assert.throws(() => validateRequest({ ...valid(), ...changed }), ApiError);
  }
});

test('storage URLs cannot embed credentials or downgrade to public HTTP', () => {
  for (const url of ['http://assets.example.com/video.mp4', 'https://user:secret@assets.example.com/video.mp4', 'file:///video.mp4']) {
    assert.throws(() => safeUrl(url), ApiError);
  }
  assert.equal(safeUrl('https://assets.example.com/video.mp4?signature=placeholder').protocol, 'https:');
  assert.equal(safeUrl('http://127.0.0.1:1234/test').protocol, 'http:');
});
