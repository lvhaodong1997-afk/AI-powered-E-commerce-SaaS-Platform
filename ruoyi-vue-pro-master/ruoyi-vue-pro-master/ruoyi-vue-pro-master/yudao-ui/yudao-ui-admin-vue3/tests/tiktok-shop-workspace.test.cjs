const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const web = path.join(__dirname, '..')
const read = (file) => fs.readFileSync(path.join(web, file), 'utf8')

test('Shop read failures stay inline and do not render an empty successful result', () => {
  const api = read('src/api/tk/tiktokShop/index.ts')
  const axios = read('src/config/axios/service.ts')
  const creator = read('src/views/tk/tiktok-shop/TiktokShopCreatorPanel.vue')
  const seller = read('src/views/tk/tiktok-shop/TiktokShopSellerPanel.vue')
  assert.match(api, /seller\/operations[^\n]+errorPresentation: 'inline'/)
  assert.match(api, /creator\/\$\{view\}[^\n]+errorPresentation: 'inline'/)
  assert.match(axios, /error\.config[^\n]*errorPresentation !== 'inline'[\s\S]*?ElMessage\.error\(message\)/)
  for (const panel of [creator, seller]) {
    assert.match(panel, /<el-alert v-if="error"[^\n]*\n\s*<el-table v-else/)
  }
  assert.match(seller, /status === 403 && reason\.response\.data\?\.code === 105005/)
  assert.match(seller, /shop\.sellerFeatureDenied/)
})

test('Shop workspace is a separate permission-gated menu and reuses the publishing panel', () => {
  const sql = read('../../yudao-module-tk/src/main/resources/sql/tk_tiktok_shop_workspace_mysql.sql')
  assert.match(sql, /'tk:tiktok-shop-workspace:query'/)
  assert.match(sql, /'tk\/tiktok-shop\/index'/)
  assert.doesNotMatch(sql, /INSERT\s+INTO\s+system_role_menu/i)
  const view = read('src/views/tk/tiktok-shop/index.vue')
  assert.match(view, /TkTiktokShopWorkspace/)
  assert.match(view, /<TiktokShopPublishPanel/)
  assert.doesNotMatch(view, /tk:video-publish-center:query/)
})

test('old publishing center links to the workspace only for users granted workspace access', () => {
  const view = read('src/views/tk/video-publish-center/index.vue')
  assert.ok(view.includes("hasPermission(['tk:tiktok-shop-workspace:query'])"))
  assert.ok(view.includes(':to="shopWorkspacePath"'))
  assert.ok(view.includes('<TiktokShopWorkspace v-else-if='))
})

test('workspace loads creator data only on its permitted tab', () => {
  const view = read('src/views/tk/tiktok-shop/index.vue')
  assert.match(view, /<TiktokShopCreatorPanel v-if="activeTab === 'creator' && canReadCreator"/)
  const creator = read('src/views/tk/tiktok-shop/TiktokShopCreatorPanel.vue')
  assert.match(creator, /TiktokShopApi\.creatorPage/)
  assert.match(creator, /revision\.current\(requestRevision\)/)
  assert.match(creator, /'marketplace'|'samples'|'orders'/)
  assert.doesNotMatch(creator, /<pre|v-html/)
})

test('a signed showcase selection opens a publishing draft for the same account', () => {
  const view = read('src/views/tk/tiktok-shop/index.vue')
  const panel = read('src/views/tk/video-publish-center/components/TiktokShopPublishPanel.vue')
  assert.match(view, /@publish="openPublish"/)
  assert.match(view, /:initial-selection="selection"/)
  assert.match(panel, /accounts\.value\.some\(\(account\) => account\.id === selection\.accountId && account\.authStatus === 'AUTHORIZED'\)/)
  assert.match(panel, /selectProduct\(selection\.product, 'SHOWCASE'\)/)
})

test('creator tabs and search are visible without an account while search needs authorization', () => {
  const creator = read('src/views/tk/tiktok-shop/TiktokShopCreatorPanel.vue')
  assert.doesNotMatch(creator, /<template v-if="accountId">/)
  assert.match(creator, /<el-tabs v-model="view"/)
  assert.match(creator, /<el-input v-model="keyword"/)
  assert.match(creator, /native-type="submit"[^>]*:disabled="!selectedAccount"/)
})

test('video and photo publishing forms are visible with publishing permission before account selection', () => {
  const video = read('src/views/tk/video-publish-center/components/TiktokShopPublishPanel.vue')
  const photo = read('src/views/tk/tiktok-shop/TiktokShopPhotoPanel.vue')
  assert.match(video, /<el-form v-if="canCreate"/)
  assert.match(video, /:disabled="!readyToCreate \|\| uploading" @click="createTask"/)
  assert.match(photo, /<el-form v-if="canCreate"/)
  assert.match(photo, /<TiktokShopProductPicker v-if="ready"/)
  assert.match(photo, /const valid = computed\(\(\) => ready\.value &&/)
})
