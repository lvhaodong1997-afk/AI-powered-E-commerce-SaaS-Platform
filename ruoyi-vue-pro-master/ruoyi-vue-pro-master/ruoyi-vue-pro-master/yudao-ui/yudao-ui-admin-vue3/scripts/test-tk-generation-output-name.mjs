import { strict as assert } from 'node:assert'

import {
  buildGenerationCreatorLabel,
  buildGenerationOutputDisplayName,
  buildGenerationOutputDownloadName
} from '../src/utils/tkGenerationOutputName.ts'

const baseTask = {
  id: 178,
  createTime: '2026-08-06 11:15:26',
  videoIndex: 2,
  creatorName: '\u5f20\u4e09',
  dailyUserVideoNo: 7,
  title: '\u83b7\u5ba2\u89c6\u9891 \u00b7 \u624b\u52a8\u5f15\u6d41\u7d20\u6750\u89c6\u9891\u751f\u6210',
  outputUrl: 'https://example.com/tk/166/166/generation-tasks/178/20260806/generated-178.mp4'
}

assert.equal(
  buildGenerationOutputDisplayName(baseTask),
  '2026-08-06-\u5f20\u4e09-007'
)

assert.equal(
  buildGenerationOutputDownloadName(baseTask),
  '2026-08-06-\u5f20\u4e09-007.mp4'
)

assert.equal(
  buildGenerationOutputDisplayName({
    id: 178,
    createTime: '2026-08-06 11:15:26',
    creator: '166',
    dailyUserVideoNo: 12
  }),
  '2026-08-06-\u7528\u6237166-012'
)

assert.equal(
  buildGenerationOutputDisplayName({
    id: 234,
    createTime: new Date(2026, 7, 7, 10, 30, 0).getTime(),
    creatorName: '\u738b\u96ea',
    dailyUserVideoNo: 1
  }),
  '2026-08-07-\u738b\u96ea-001'
)

assert.equal(
  buildGenerationOutputDisplayName({ id: 179, outputUrl: 'https://example.com/generated-179.mp4' }),
  '\u4efb\u52a1179'
)

assert.equal(
  buildGenerationOutputDownloadName({ id: 179, outputUrl: 'https://example.com/generated-179.mp4' }),
  'TK\u89c6\u9891_\u4efb\u52a1179.mp4'
)

const signedOutputUrl =
  'https://oss.example.com/tk/5/9/generation-tasks/43/20260807/generated-43.mp4' +
  '?OSSAccessKeyId=ak&Expires=1786075200' +
  '&response-content-disposition=attachment%3B%20filename%3D%22TK-video_20260807_task43.mp4%22' +
  '&Signature=sig'

assert.equal(
  buildGenerationOutputDisplayName({
    id: 43,
    createTime: '2026-08-07 10:30:00',
    creatorName: '\u7ba1\u7406\u5458',
    dailyUserVideoNo: 1,
    outputUrl: signedOutputUrl
  }),
  '2026-08-07-\u7ba1\u7406\u5458-001'
)

assert.equal(
  buildGenerationOutputDownloadName({
    id: 43,
    createTime: '2026-08-07 10:30:00',
    creatorName: '\u7ba1\u7406\u5458',
    dailyUserVideoNo: 1,
    outputUrl: signedOutputUrl
  }),
  '2026-08-07-\u7ba1\u7406\u5458-001.mp4'
)

assert.equal(buildGenerationCreatorLabel({ creator: '166' }), '\u751f\u6210\u7528\u6237\uff1a\u7528\u6237ID 166')

assert.equal(buildGenerationCreatorLabel({ creator: '' }), '\u751f\u6210\u7528\u6237\uff1a-')

console.log('tk generation output name tests passed')
