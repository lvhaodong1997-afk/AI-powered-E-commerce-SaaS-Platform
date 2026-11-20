const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const page = fs.readFileSync(path.join(__dirname,
  '../src/views/tk/channel-publish/ChannelPublishWorkspace.vue'), 'utf8')
const api = fs.readFileSync(path.join(__dirname, '../src/api/tk/channelPublish/index.ts'), 'utf8')

assert.match(page, /type="file"[^>]+accept="\.mp4,video\/mp4"/)
assert.match(page, /<MetaGeneratedPicker/)
assert.match(page, /uploadSocialVideoToOss\(/)
assert.match(page, /async function pollUpload\(/)
assert.match(page, /metadataStatus === 'VERIFIED'/)
assert.match(page, /mediaId: uploadedVideo\.value\.id.*generationTaskId: String\(video\.value\??\.id\)/s)
assert.match(api, /media\/video-upload-session/)
assert.match(api, /media\/video-upload-complete/)
assert.match(api, /media\/get/)
