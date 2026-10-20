const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')
const { createRequire } = require('node:module')
const { compile } = createRequire(require.resolve('vue'))('@vue/compiler-dom')

const filename = path.resolve(
  __dirname,
  '../src/views/tk/dashboard/components/MiniMaxVoiceSelector.vue'
)
const source = fs.readFileSync(filename, 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]+?)<\/script>/)[1]
const template = source.slice(source.indexOf('<template>') + 10, source.lastIndexOf('</template>'))
const render = new Function('Vue', compile(template, { mode: 'function' }).code)({
  ...vue,
  resolveComponent: (name) => name,
  resolveDirective: () => () => {},
  withDirectives: (node) => node
})
function harness(provider = 'MINIMAX') {
  const props = vue.reactive({
    modelValue: 'VoiceA',
    provider,
    options: [{ voiceId: 'VoiceA', label: 'Voice A', favorite: true }],
    disabled: false
  })
  const emitted = [],
    errors = [],
    writes = []
  let reject = false
  const api = {
    favorite: async (...args) => {
      writes.push(['add', ...args])
      if (reject) throw Error('offline')
    },
    unfavorite: async (...args) => {
      writes.push(['remove', ...args])
      if (reject) throw Error('offline')
    }
  }
  const context = vm.createContext({
    exports: {},
    defineProps: () => props,
    defineEmits:
      () =>
      (event, ...args) => {
        emitted.push([event, ...args])
        if (event === 'favorite-change') {
          props.options = props.options.filter(
            (x) => !x.unavailable || x.voiceId !== args[0] || args[1]
          )
          props.options.forEach((x) => {
            if (x.voiceId === args[0]) x.favorite = args[1]
          })
        }
      },
    require: (name) => {
      if (name === 'vue') return { ...vue, onBeforeUnmount: () => {}, watch: () => {} }
      if (name === 'element-plus') return { ElMessage: { error: (value) => errors.push(value) } }
      if (name === '@/api/tk/voice') return { TkMiniMaxVoiceApi: api, TkVoiceFavoriteApi: api }
      if (name === '@/hooks/web/useTkI18n') return { useTkI18n: () => ({ tt: (key) => key }) }
      return require(path.resolve(path.dirname(filename), name))
    }
  })
  const expose =
    '\nglobalThis.selector = { props, filter, filteredOptions, selectVoice, toggleFavorite, tt, languageOptions, countryOptions, genderOptions, playingVoiceId, togglePreview, favoriteState, favoriteCount, getVoiceEmptyState }'
  vm.runInContext(
    ts.transpileModule(script + expose, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
    }).outputText,
    context
  )
  // Template context evaluates setup bindings without mounting a browser or replacing business functions.
  const ctx = new Proxy(vue.proxyRefs(context.selector), {
    has: (_target, key) => !String(key).startsWith('_') && !(key in globalThis),
    get: (target, key) => (key in props ? props[key] : key in target ? target[key] : undefined)
  })
  return {
    props,
    emitted,
    writes,
    errors,
    selector: context.selector,
    ctx,
    failWrites: () => {
      reject = true
    }
  }
}
function find(node, predicate) {
  if (!node || typeof node !== 'object') return
  if (predicate(node)) return node
  const children = Array.isArray(node.children)
    ? node.children
    : typeof node.children?.default === 'function'
      ? node.children.default()
      : []
  for (const child of children) {
    const found = find(child, predicate)
    if (found) return found
  }
}
async function main() {
  const h = harness()
  // Execute the compiled template's real bubbling guards: keyboard activation on stars must never select.
  const tree = render(h.ctx, [])
  const actions = find(tree, (node) => node.props?.class === 'minimax-voice-actions')
  assert.equal(
    typeof actions.props.onKeydown,
    'function',
    'Actions must stop keyboard events before they reach the selectable row'
  )
  let stopped = false
  actions.props.onKeydown({
    key: 'Enter',
    stopPropagation: () => {
      stopped = true
    }
  })
  assert.equal(stopped, true)
  assert.equal(h.selector.favoriteCount.value, 1)
  const star = find(
    tree,
    (node) => node.type === 'el-button' && node.props?.['aria-label'] === 'voice.removeFavorite'
  )
  const write = star.props.onClick()
  stopped = false
  actions.props.onClick({
    stopPropagation: () => {
      stopped = true
    }
  })
  assert.equal(stopped, true, 'Mouse click on the star cannot bubble to selection')
  await write
  assert.equal(h.selector.favoriteCount.value, 0)
  h.selector.filter.favoritesOnly = true
  assert.equal(h.selector.filteredOptions.value.length, 0)
  assert.equal(
    find(render(h.ctx, []), (node) => node.type === 'el-empty').props.description,
    'voice.noFavorites'
  )
  assert.equal(h.props.modelValue, 'VoiceA')
  assert.ok(!h.emitted.some((x) => x[0] === 'update:modelValue'))
  assert.deepEqual(h.writes, [['remove', 'VoiceA']])
  assert.equal(h.props.options[0].favorite, false)
  h.failWrites()
  await h.selector.toggleFavorite(h.props.options[0])
  assert.equal(h.props.options[0].favorite, false)
  assert.deepEqual(h.errors, ['voice.favoriteFailed'])
  const dash = harness('DASHSCOPE')
  await dash.selector.toggleFavorite(dash.props.options[0])
  assert.deepEqual(dash.writes, [['remove', 'DASHSCOPE', 'VoiceA']])
  dash.props.options.push({
    voiceId: 'custom:9',
    label: 'Deleted',
    favorite: true,
    unavailable: true
  })
  const staleRow = find(render(dash.ctx, []), (node) => node.props?.['aria-disabled'] === true)
  assert.ok(staleRow, 'Deleted favorites are disabled choices')
  const staleStar = find(
    staleRow,
    (node) => node.type === 'el-button' && node.props?.['aria-label'] === 'voice.removeFavorite'
  )
  assert.ok(!staleStar.props.disabled, 'Deleted favorites still have an operable remove button')
  dash.selector.selectVoice('custom:9')
  assert.ok(
    !dash.emitted.some((x) => x[0] === 'update:modelValue'),
    'Unavailable favorite cannot become a selected generation voice'
  )
  dash.props.favoritesDisabled = true
  await dash.selector.toggleFavorite(dash.props.options[0])
  assert.equal(
    dash.writes.length,
    1,
    'A failed favorites load must disable writes while keeping voice selection usable'
  )
  dash.selector.selectVoice('VoiceA')
  assert.ok(dash.emitted.some((x) => x[0] === 'update:modelValue'))
  dash.props.favoritesDisabled = false
  dash.props.disabled = true
  await dash.selector.toggleFavorite(dash.props.options[0])
  assert.equal(dash.writes.length, 1, 'Disabled controls cannot write')
  console.log(
    'Compiled selector keyboard guards, selection preservation, provider routing and write failure tests passed'
  )
}
main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
