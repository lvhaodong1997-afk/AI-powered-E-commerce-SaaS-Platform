const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const ts = require('typescript')
const { createRouter, createMemoryHistory } = require('vue-router')

const Layout = { name: 'TestLayout', render: () => null }
const source = fs.readFileSync(path.join(__dirname, '../src/router/modules/remaining.ts'), 'utf8')
const context = {
  exports: {},
  require: (id) => {
    assert.equal(id, '@/utils/routerHelper')
    return { Layout }
  },
  useI18n: () => ({ t: (key) => key })
}
vm.runInNewContext(
  ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText,
  context
)

for (const [slug, componentName, title] of [
  ['recharge', 'TkRecharge', '积分充值'],
  ['invite', 'TkInvite', '邀请推荐']
]) {
  test(`${slug} keeps its page and title when authenticated menu routes are added`, () => {
    const router = createRouter({ history: createMemoryHistory(), routes: context.exports.default })
    // Same structure as the backend /tk menu, registered after the fixed routes at login.
    router.addRoute({
      path: '/tk',
      name: 'Tk',
      component: Layout,
      children: [
        { path: slug, name: componentName, component: { render: () => null }, meta: { title } }
      ]
    })

    const resolved = router.resolve(`/tk/${slug}`)
    assert.equal(resolved.meta.title, title, 'menu navigation must retain a titled page')
    assert.notEqual(
      resolved.matched.at(-1).components.default,
      Layout,
      'must render a page inside Layout'
    )
    assert.equal(router.resolve({ name: componentName }).path, `/tk/${slug}`)
  })

  test(`${slug} direct entry remains available before dynamic menus load`, () => {
    const router = createRouter({ history: createMemoryHistory(), routes: context.exports.default })
    const resolved = router.resolve(`/tk/${slug}`)
    assert.equal(resolved.meta.title, title)
    assert.equal(typeof resolved.matched.at(-1).components.default, 'function')
  })
}
