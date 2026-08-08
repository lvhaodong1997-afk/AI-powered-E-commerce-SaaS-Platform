import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import vm from 'node:vm'
import ts from 'typescript'

const require = createRequire(import.meta.url)
const root = process.cwd()
const sourcePath = path.join(root, 'src/utils/tkTextSanitizer.ts')
const source = fs.readFileSync(sourcePath, 'utf8')
assert.equal(source.includes('Translated content'), false)
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2022
  }
}).outputText

const module = { exports: {} }
const testRequire = (id) => {
  if (id === '@/locales/tk') {
    return {
      tkEnglishTextMap: {
        '素材库': 'Materials',
        '素材库数量': 'Material Libraries',
        '当前视频数': 'Current Videos',
        '视频数': 'Videos',
        '容量': 'Size',
        '上传': 'Upload',
        '新建素材库': 'New Material Library',
        '上传到当前素材库': 'Upload to Current Library',
        '全部用途': 'All Uses',
        '修改已选用途': 'Change Selected Use',
        'S1 黄金3秒': 'S1 Golden 3 Seconds',
        'S1 黄金3s': 'S1 Golden 3 Seconds',
        'S2 痛点场景': 'S2 Pain-point Scene',
        '痛点场景': 'Pain-point Scene',
        '缺少关键用途': 'Missing key uses',
        '系统管理': 'System Management',
        'TK素材工厂': 'TK Material Factory',
        '直接发布': 'Publish Now',
        '发送草稿箱': 'Send to Drafts',
        'TikTok 授权完成': 'TikTok authorization complete',
        '仅自己可见': 'Only Me',
        '好友可见': 'Friends',
        '粉丝可见': 'Followers',
        '公开': 'Public',
        '商业内容': 'Commercial Content',
        '品牌内容': 'Branded Content',
        '12秒': '12s',
        '16秒': '16s',
        '20秒': '20s',
        '系统音色': 'System Voices',
        '我的音色': 'My Voices',
        '管理我的音色': 'Manage My Voices',
        '待发布视频': 'Videos to Publish',
        '账号矩阵': 'Account Matrix',
        '视频发布中心': 'Video Publishing Center'
      }
    }
  }
  return require(id)
}
vm.runInNewContext(compiled, {
  exports: module.exports,
  module,
  require: testRequire,
  console
})

const {
  containsCjk,
  sanitizeEnglishVisibleText,
  sanitizeEnglishAttributeText,
  sanitizeEnglishTitleText
} = module.exports

assert.equal(containsCjk('Material Library'), false)
assert.equal(containsCjk('素材库'), true)

assert.equal(sanitizeEnglishVisibleText('素材库'), 'Materials')
assert.equal(sanitizeEnglishVisibleText('素材视频 · 脚步提拉带'), 'Material Videos · Foot Lift Strap')
assert.equal(sanitizeEnglishVisibleText('秀美二楼'), 'Xiumei 2F')
assert.equal(sanitizeEnglishVisibleText('视频 7'), 'Video 7')
assert.equal(sanitizeEnglishVisibleText('待发布视频'), 'Videos to Publish')
assert.equal(sanitizeEnglishVisibleText('账号矩阵'), 'Account Matrix')
assert.equal(sanitizeEnglishVisibleText('视频发布中心'), 'Video Publishing Center')
assert.equal(sanitizeEnglishVisibleText('新建分组'), 'New Group')
assert.equal(sanitizeEnglishVisibleText('未授权'), 'Unauthorized')
assert.equal(sanitizeEnglishVisibleText('用户已解绑 TikTok 授权'), 'The user has unbound TikTok authorization')
assert.equal(sanitizeEnglishVisibleText('管理员'), 'Administrator')
assert.equal(sanitizeEnglishVisibleText('素材库数量'), 'Material Libraries')
assert.equal(sanitizeEnglishVisibleText('当前视频数'), 'Current Videos')
assert.equal(sanitizeEnglishVisibleText('视频数'), 'Videos')
assert.equal(sanitizeEnglishVisibleText('容量'), 'Size')
assert.equal(sanitizeEnglishVisibleText('上传'), 'Upload')
assert.equal(sanitizeEnglishVisibleText('新建素材库'), 'New Material Library')
assert.equal(sanitizeEnglishVisibleText('上传到当前素材库'), 'Upload to Current Library')
assert.equal(sanitizeEnglishVisibleText('全部用途'), 'All Uses')
assert.equal(sanitizeEnglishVisibleText('修改已选用途'), 'Change Selected Use')
assert.equal(sanitizeEnglishVisibleText('S1 黄金3s'), 'S1 Golden 3 Seconds')
assert.equal(sanitizeEnglishVisibleText('S2 痛点场景'), 'S2 Pain-point Scene')
assert.equal(sanitizeEnglishVisibleText('痛点场景'), 'Pain-point Scene')
assert.equal(sanitizeEnglishVisibleText('S2 痛点场景').includes('credits'), false)
assert.equal(sanitizeEnglishVisibleText('提炼卖点细节'), '提炼卖点细节')
assert.equal(sanitizeEnglishVisibleText('提炼卖点细节').includes('credits'), false)
assert.equal(sanitizeEnglishVisibleText('系统管理'), 'System Management')
assert.equal(sanitizeEnglishVisibleText('TK素材工厂'), 'TK Material Factory')
assert.equal(sanitizeEnglishVisibleText('直接发布'), 'Publish Now')
assert.equal(sanitizeEnglishVisibleText('发送草稿箱'), 'Send to Drafts')
assert.equal(sanitizeEnglishVisibleText('TikTok 授权完成'), 'TikTok authorization complete')
assert.equal(sanitizeEnglishVisibleText('仅自己可见'), 'Only Me')
assert.equal(sanitizeEnglishVisibleText('商业内容'), 'Commercial Content')
assert.equal(sanitizeEnglishVisibleText('12秒'), '12s')
assert.equal(sanitizeEnglishVisibleText('系统音色'), 'System Voices')
assert.equal(sanitizeEnglishVisibleText('剪辑明细'), 'Clip Details')
assert.equal(sanitizeEnglishVisibleText('生成详情'), 'Generation Details')
assert.equal(sanitizeEnglishVisibleText('业务流水号已复制'), 'Business trace ID copied')
assert.equal(
  sanitizeEnglishVisibleText('把商品卖点变成可投放创意'),
  'Turn product selling points into launch-ready creatives'
)
assert.equal(sanitizeEnglishVisibleText('尚未收录的新中文'), '尚未收录的新中文')
assert.equal(sanitizeEnglishVisibleText('Total 3'), 'Total 3')

assert.equal(sanitizeEnglishAttributeText('请输入菜单内容'), 'Please enter menu content')
assert.equal(sanitizeEnglishAttributeText('秀美二楼'), 'Xiumei 2F')

assert.equal(sanitizeEnglishTitleText('ClipForge Studio - 素材库'), 'ClipForge Studio - Materials')
assert.equal(containsCjk(sanitizeEnglishTitleText('ClipForge Studio - 首页')), false)

console.log('tkTextSanitizer checks passed')
