const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const page = fs.readFileSync(path.join(__dirname,
  '../src/views/tk/channel-publish/ChannelPublishWorkspace.vue'), 'utf8')

assert.match(page, /<TenantVisit\s+v-if="isPlatformAdmin"/, 'provide the existing tenant selector on the publishing page')
assert.match(page, /needsTenant\s*=.*isPlatformAdmin\.value\s*&&\s*!getVisitTenantId\(\)/, 'only unscoped platform administrators need to select a tenant')
assert.match(page, /<el-alert[^>]+v-if="needsTenant"[^>]+type="warning"/, 'missing tenant is a recoverable state, not a load error')
assert.match(page, /v-if="!needsTenant"[^>]+aria-labelledby="channel-create"/, 'hide the publishing form outside a tenant')
assert.match(page, /v-if="!needsTenant"[^>]+aria-labelledby="channel-history"/, 'hide the misleading empty history outside a tenant')
assert.match(page, /if \(!needsTenant\.value\) await Promise\.all\(\[loadAccounts\(\), loadHistory\(\)\]\)/, 'avoid calling tenant-scoped APIs until a tenant is selected')
assert.match(page, /async function authorize\(\) \{\s*if \(needsTenant\.value\) return\s*const popup/, 'guard the authorization popup outside a tenant')
