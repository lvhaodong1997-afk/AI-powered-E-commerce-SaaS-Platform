import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const collection = JSON.parse(fs.readFileSync(new URL('../tk-automation.postman_collection.json', import.meta.url), 'utf8'));
const environment = JSON.parse(fs.readFileSync(new URL('../tk-automation.postman_environment.json', import.meta.url), 'utf8'));
const create = collection.item.find(item => item.name.startsWith('07'));
const source = create.event.find(event => event.listen === 'prerequest').script.exec.join('\n');

function run(overrides = {}) {
  const values = Object.fromEntries(environment.values.map(value => [value.key, value.value]));
  Object.assign(values, overrides);
  const result = {values, skipped: false, body: undefined, error: undefined};
  const pm = {
    environment: {get: key => values[key], set: (key, value) => { values[key] = value; }},
    execution: {skipRequest: () => { result.skipped = true; }},
    request: {body: {update: body => { result.body = body; }}}
  };
  try { vm.runInNewContext(source, {pm}); } catch (error) { result.error = error; }
  return result;
}

assert.equal(run().skipped, true, 'default imported environment cannot start a paid task');
assert.equal(run().body, undefined);
const valid = {allowPaidCreate: 'true', libraryId: '123', bgmAssetId: '456',
  ttsProvider: 'MINIMAX', voiceCode: 'from-list', scriptText: '一段含"引号"\n与换行的口播。'};
const sent = run(valid);
assert.equal(sent.error, undefined);
assert.equal(sent.skipped, false);
assert.equal(sent.values.allowPaidCreate, 'false', 'opt-in is consumed before sending');
assert.equal(JSON.parse(sent.body).scriptText, valid.scriptText, 'text must survive JSON escaping');
assert.equal(JSON.parse(sent.body).voiceCode, 'from-list');
assert.equal(Object.hasOwn(JSON.parse(sent.body), 'voiceProfileId'), false);
assert.equal(run({...valid, taskId: '789'}).skipped, true, 'existing task must be checked before new paid create');
assert.equal(run({...valid, ttsProvider: 'MIMO'}).skipped, true, 'MiMo requires profile ID');
assert.equal(run({...valid, voiceProfileId: '321'}).skipped, true, 'voice identifiers are mutually exclusive');
assert.equal(run({...valid, scriptText: ''}).skipped, true);
const profile = run({...valid, ttsProvider: 'MIMO', voiceCode: '', voiceProfileId: '321'});
assert.equal(profile.error, undefined);
assert.equal(JSON.parse(profile.body).voiceProfileId, 321);
assert.equal(Object.hasOwn(JSON.parse(profile.body), 'voiceCode'), false);
const largeId = run({...valid, libraryId: '9007199254740993'});
assert.match(largeId.body, /"libraryId": 9007199254740993[,\n]/, 'do not round an int64 identifier through JS Number');
console.log('Postman exported scripts: paid-create opt-in, duplicate guard, voice choice, JSON text, int64 IDs passed');
