<template>
  <div ref="pageRoot" class="clipforge-theme api-docs-page" data-tk-i18n-ignore>
    <header class="docs-topbar">
      <RouterLink class="docs-brand" to="/"
        ><img src="/clipforge-app-icon.png?v=20260728-system-icon" alt="" /><span
          >ClipForge <b>Developers</b></span
        ></RouterLink
      >
      <div class="docs-top-actions"
        ><span class="docs-version">API v1</span
        ><RouterLink to="/login" class="console-link button-primary"
          >进入工作台 <span aria-hidden="true">↗</span></RouterLink
        ></div
      >
    </header>
    <div class="docs-layout">
      <aside class="docs-sidebar" aria-label="文档目录">
        <div class="docs-sidebar-head"
          ><span>开发者文档</span
          ><button
            type="button"
            class="mobile-nav-toggle"
            :aria-expanded="navOpen"
            @click="navOpen = !navOpen"
            >{{ navOpen ? '收起目录 −' : '展开目录 +' }}</button
          ></div
        >
        <label class="docs-search"
          ><span class="sr-only">搜索接口</span><span aria-hidden="true">⌕</span
          ><input v-model="query" placeholder="搜索接口、方法或关键词" type="search"
        /></label>
        <nav v-show="navOpen || !mobile" class="docs-nav">
          <template v-if="!query.trim()">
            <p class="nav-label">开始接入</p>
            <a
              href="#overview"
              :class="{ selected: activeId === 'overview' }"
              @click.prevent="selectTopic('overview')"
              >概览 <span>01</span></a
            >
            <a
              v-for="guide in catalog?.guides || []"
              :key="guide.id"
              :href="`#guide-${guide.id}`"
              :class="{ selected: activeId === `guide-${guide.id}` }"
              @click.prevent="selectTopic(`guide-${guide.id}`)"
              >{{ guide.title }}</a
            >
            <p class="nav-label"
              >接口参考 <span>{{ operations.length }}</span></p
            >
          </template>
          <template v-for="group in groupedOperations" :key="group.name">
            <p class="nav-group">{{ group.name }}</p>
            <a
              v-for="operation in group.items"
              :key="operation.id"
              :href="`#${operation.id}`"
              :class="{ selected: activeId === operation.id }"
              @click.prevent="selectTopic(operation.id)"
            >
              <span class="method-mini" :class="operation.method">{{
                operation.method.toUpperCase()
              }}</span
              ><span>{{ operation.summary }}</span>
            </a>
          </template>
          <p v-if="query.trim() && !filteredOperations.length" class="docs-empty"
            >没有匹配接口，试试“生成”或“POST”。</p
          >
          <template v-if="!query.trim()"
            ><p class="nav-label">接入资料</p
            ><a
              href="#downloads"
              :class="{ selected: activeId === 'downloads' }"
              @click.prevent="selectTopic('downloads')"
              >示例与工具下载 <span>↗</span></a
            ><a
              href="#changelog"
              :class="{ selected: activeId === 'changelog' }"
              @click.prevent="selectTopic('changelog')"
              >版本与更新记录</a
            ></template
          >
        </nav>
        <div class="sidebar-footer"
          >ClipForge Studio<br /><span>接口说明 · 调用规则 · 完整示例</span></div
        >
      </aside>

      <main id="docs-content" class="docs-main">
        <div v-if="loading" class="docs-state" role="status">正在加载接口文档…</div>
        <div v-else-if="error" class="docs-state" role="alert"
          ><h1>文档暂时无法加载</h1><p>{{ error }}</p
          ><button type="button" class="button-primary" @click="loadCatalog">重新加载</button></div
        >
        <template v-else-if="catalog">
          <template v-if="activeId === 'overview'">
            <div class="docs-breadcrumb">文档 / 开始接入</div>
            <section class="docs-intro">
              <div class="intro-copy"
                ><p class="docs-eyebrow">CLIPFORGE API / DEVELOPER CENTER</p
                ><h1>把出片流程，接进你的应用。</h1>
                <p class="intro-text"
                  >从完整口播到可下载的视频，用 API
                  连接素材、配音、字幕与混剪。按照接入步骤，完成你的第一次调用。</p
                >
                <div class="intro-actions"
                  ><button
                    type="button"
                    class="button-primary"
                    @click="selectTopic('guide-quickstart')"
                    >开始接入 <span>→</span></button
                  ><a href="/api-reference/clipforge-api-kit.zip" class="button-secondary" download
                    >下载完整接入包 ↓</a
                  ></div
                >
                <p class="intro-caption">包含 Python / Node.js 示例、Postman 与 OpenAPI 文件</p>
              </div>
              <div class="intro-guide-panel"
                ><p>接入指南 <span>从第一步开始</span></p
                ><a
                  v-for="(guide, index) in catalog.guides.slice(0, 3)"
                  :key="guide.id"
                  :href="'#guide-' + guide.id"
                  @click.prevent="selectTopic('guide-' + guide.id)"
                  ><span class="guide-number">0{{ index + 1 }}</span
                  ><strong>{{ guide.title }}</strong
                  ><span aria-hidden="true">↗</span></a
                ></div
              >
            </section>
            <section class="docs-auth-cards"
              ><article
                ><span class="section-index">01 / 自动出片</span><h2>账号 Token</h2
                ><p>使用自己的账号与租户，查询素材、提交任务、获取成片。</p
                ><code>Authorization: Bearer …</code></article
              ><article
                ><span class="section-index">02 / TikTok 发布</span><h2>HMAC 请求签名</h2
                ><p>使用开通的调用方凭证，完成授权、上传和发布。</p
                ><code>X-TK-Client-Id · X-TK-Signature</code></article
              ></section
            >
            <section class="docs-section"
              ><h2>一次完整的出片调用</h2
              ><p class="section-description">任务异步执行。提交后保留任务号，再查询结果和下载。</p
              ><div class="workflow-strip"
                ><button
                  v-for="(step, i) in workflow"
                  :key="step.title"
                  type="button"
                  @click="selectByPath(step.path)"
                  ><span>0{{ i + 1 }}</span
                  ><strong>{{ step.title }}</strong
                  ><small>{{ step.detail }}</small
                  ><i v-if="i < 3" aria-hidden="true">→</i></button
                ></div
              ></section
            >
            <section class="docs-section"
              ><h2>开始前，准备好这些</h2
              ><div class="check-list"
                ><p><b>账号与租户</b>使用实际登录账号；验证码沿用平台原有要求。</p
                ><p><b>可用资源</b>先查素材库、音色和自己的上传 BGM，再使用返回的 ID。</p
                ><p><b>任务处理</b>只以业务成功和任务终态判断结果；创建超时不要重复提交。</p></div
              ></section
            >
            <div class="docs-note"
              >文档公开可读，业务接口仍执行登录、租户与权限校验。这里的示例不会自动发起生成或消耗积分。</div
            >
          </template>

          <template v-else-if="activeGuide">
            <div class="docs-breadcrumb">文档 / {{ activeGuide.title }}</div
            ><p class="docs-eyebrow">INTEGRATION GUIDE</p><h1>{{ activeGuide.title }}</h1
            ><p class="section-description">{{ activeGuide.hint }}</p
            ><ApiDocMarkdown :source="activeGuide.content" @copy="copy" />
          </template>

          <template v-else-if="activeOperation">
            <div class="docs-breadcrumb">接口参考 / {{ activeOperation.tags?.[0] }}</div>
            <div class="endpoint-title"
              ><h1>{{ activeOperation.summary }}</h1
              ><span class="auth-badge">{{
                requestExample?.signed ? 'HMAC 签名' : securityLabel
              }}</span></div
            >
            <div class="endpoint-path"
              ><span class="method-label" :class="activeOperation.method">{{
                activeOperation.method.toUpperCase()
              }}</span
              ><code>{{ activeOperation.path }}</code
              ><button
                type="button"
                title="复制接口地址"
                aria-label="复制接口地址"
                @click="copy(requestExample.url)"
                >复制</button
              ></div
            >
            <div class="operation-layout"
              ><div class="operation-body">
                <ApiDocMarkdown
                  :source="
                    activeOperation.description ||
                    '按下方参数与示例调用。资源编号请使用自己账号查询到的真实值。'
                  "
                  @copy="copy"
                />
                <div v-if="activeOperation.path.includes('/automation/')" class="docs-note"
                  >请求头需要账号 Token 和
                  tenant-id。提交生成任务会沿用平台积分规则；示例中的资源编号必须替换。生成创建接口不提供幂等去重。</div
                >
                <div v-if="requestExample.signed" class="docs-note"
                  >示例中的签名头是占位符。请按照“TikTok
                  发布接入”的规则，对实际发送的方法、路径、查询串和请求体签名。</div
                >
                <section v-if="parameters.length" class="docs-section"
                  ><h2>请求参数</h2
                  ><div class="doc-table-scroll"
                    ><table
                      ><thead
                        ><tr><th>字段</th><th>位置 / 类型</th><th>必填</th><th>说明</th></tr></thead
                      ><tbody
                        ><tr v-for="param in parameters" :key="`${param.in}-${param.name}`"
                          ><td
                            ><code>{{ param.name }}</code></td
                          ><td
                            >{{ param.in }} /
                            {{ resolveSchema(catalog.spec, param.schema).type || 'string' }}</td
                          ><td>{{ param.required ? '是' : '否' }}</td
                          ><td
                            ><ApiDocText
                              :text="param.description || '—'" /></td></tr></tbody></table></div
                ></section>
                <section v-if="bodyRows.length" class="docs-section"
                  ><h2
                    >请求体 <small>{{ bodyMediaType }}</small></h2
                  ><p v-if="bodySchema.description" class="section-description">{{
                    bodySchema.description
                  }}</p
                  ><div class="doc-table-scroll"
                    ><table
                      ><thead
                        ><tr><th>字段</th><th>类型 / 必填</th><th>说明与限制</th></tr></thead
                      ><tbody
                        ><tr v-for="field in bodyRows" :key="field.name"
                          ><td
                            ><code>{{ field.name }}</code></td
                          ><td
                            >{{ field.type
                            }}<span v-if="field.required" class="required-mark"> 必填</span></td
                          ><td
                            ><ApiDocText :text="field.description" /><small
                              v-if="field.constraint"
                              class="field-constraint"
                              >{{ field.constraint }}</small
                            ></td
                          ></tr
                        ></tbody
                      ></table
                    ></div
                  ></section
                >
                <div class="docs-note"
                  >HTTP 成功不等于业务成功。账号接口检查 code=0；TikTok
                  签名接口按其响应格式判断。异步任务继续检查 status、失败原因和字幕诊断字段。</div
                > </div
              ><aside class="operation-examples" aria-label="请求与响应示例"
                ><section class="docs-section"
                  ><div class="section-heading"
                    ><h2>调用示例</h2
                    ><div class="code-tabs" aria-label="示例语言"
                      ><button
                        v-for="language in languages"
                        :key="language.id"
                        type="button"
                        :class="{ active: codeLanguage === language.id }"
                        @click="codeLanguage = language.id"
                        >{{ language.label }}</button
                      ></div
                    ></div
                  ><p v-if="requestExample.multipart || requestExample.binary" class="docs-note"
                    >这是文件上传接口，请准备示例指定的实际文件。{{
                      requestExample.signed ? '文件内容参与请求签名，请参考签名教程。' : ''
                    }}</p
                  ><div class="doc-code"
                    ><div class="doc-code-bar"
                      ><span>{{
                        codeLanguage === 'javascript' ? 'NODE.JS' : codeLanguage.toUpperCase()
                      }}</span
                      ><button type="button" @click="copy(requestExample[codeLanguage])"
                        >复制代码</button
                      ></div
                    >><pre><code>{{ requestExample[codeLanguage] }}</code></pre></div
                  ><p class="snippet-caption"
                    >单接口示例用于理解请求格式。自动刷新、异常处理和完整出片流程，请使用<a
                      href="#downloads"
                      @click.prevent="selectTopic('downloads')"
                      >完整示例</a
                    >。</p
                  ></section
                >
                <section class="docs-section"
                  ><div class="section-heading"
                    ><h2>响应示例</h2
                    ><label class="response-select"
                      >响应
                      <select v-model="responseStatus"
                        ><option
                          v-for="status in Object.keys(activeOperation.responses || {})"
                          :key="status"
                          :value="status"
                          >{{ status }}</option
                        ></select
                      ></label
                    ></div
                  ><div class="doc-code"
                    ><div class="doc-code-bar"
                      ><span>JSON</span
                      ><button type="button" @click="copy(responseExample)">复制响应</button></div
                    >><pre><code>{{ responseExample }}</code></pre></div
                  ><div v-if="responseRows.length" class="doc-table-scroll"
                    ><table
                      ><thead
                        ><tr><th>字段</th><th>类型</th><th>说明</th></tr></thead
                      ><tbody
                        ><tr v-for="field in responseRows" :key="field.name"
                          ><td
                            ><code>{{ field.name }}</code></td
                          ><td>{{ field.type }}</td
                          ><td
                            ><ApiDocText
                              :text="
                                field.description || field.constraint || '—'
                              " /></td></tr></tbody></table></div
                ></section> </aside
            ></div>
          </template>

          <template v-else-if="activeId === 'downloads'">
            <div class="docs-breadcrumb">文档 / 接入资料</div
            ><p class="docs-eyebrow">READY TO INTEGRATE</p><h1>示例与工具下载</h1
            ><p class="section-description"
              >选择你熟悉的方式开始。完整示例包含资源查询、任务提交、状态轮询和视频下载；所有凭证均需使用自己的配置。</p
            ><a
              class="button-primary bundle-download"
              href="/api-reference/clipforge-api-kit.zip"
              download
              >下载完整接入包 <span>↓ ZIP</span></a
            ><div class="downloads-list"
              ><a
                v-for="item in catalog.downloads"
                :key="item.file"
                :href="`/api-reference/${item.file}`"
                download
                ><div
                  ><strong>{{ item.title }}</strong
                  ><p>{{ item.detail }}</p
                  ><code>{{ item.file }}</code></div
                ><span aria-hidden="true">↓</span></a
              ></div
            ><div class="docs-note"
              >示例脚本提供 resources、create 和 resume 命令。只有显式执行 create
              才会提交收费生成任务；先阅读 examples/README.md。</div
            >
          </template>

          <template v-else-if="activeId === 'changelog'"
            ><div class="docs-breadcrumb">文档 / 更新记录</div><p class="docs-eyebrow">CHANGELOG</p
            ><h1>版本与更新记录</h1
            ><section class="change-entry"
              ><time>2026.11.14</time><h2>接入文档中心上线</h2
              ><p
                >新增在线接口参考、完整出片教程、Python / Node.js 示例、OpenAPI 与 Postman
                下载。更新生成能力说明，区分账号 Token 和 TikTok 签名鉴权。</p
              ></section
            ><section class="change-entry"
              ><time>2026.11.12</time><h2>自动出片 API v1</h2
              ><p
                >提供积分、素材库、音色、BGM、创建任务、进度和下载接口；支持完整口播、配音时长跟随及字幕配置。</p
              ></section
            ><div class="docs-note"
              >当前出片接口不支持按标签选画面、幂等创建或任务
              Webhook。接口兼容性以路径版本及具体字段约定为准。</div
            ></template
          >
          <div v-else class="docs-state"
            ><h1>没有找到这个文档章节</h1
            ><button class="button-primary" type="button" @click="selectTopic('overview')"
              >返回概览</button
            ></div
          >
          <footer class="docs-footer"
            ><span>ClipForge Studio · API 文档</span
            ><a href="mailto:shangan@zswo.net">接入支持 ↗</a></footer
          >
        </template>
      </main>
    </div>
    <div v-if="copyMessage" class="docs-toast" role="status">{{ copyMessage }}</div>
  </div>
</template>

<script setup lang="ts">
import '@/styles/clipforge-theme.css'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ApiDocMarkdown from './ApiDocMarkdown.vue'
import ApiDocText from './ApiDocText.vue'
import {
  operationExample,
  resolveSchema,
  sampleValue,
  schemaRows,
  searchOperations
} from './apiDocs.mjs'

type Operation = Record<string, any> & { id: string; path: string; method: string; summary: string }
type Catalog = {
  version: string
  spec: Record<string, any>
  guides: Array<{ id: string; title: string; hint: string; content: string }>
  downloads: Array<{ file: string; title: string; detail: string }>
}
const route = useRoute(),
  router = useRouter()
const pageRoot = ref<HTMLElement>()
const catalog = ref<Catalog>(),
  loading = ref(true),
  error = ref(''),
  query = ref('')
const navOpen = ref(false),
  mobile = ref(false),
  codeLanguage = ref('curl'),
  responseStatus = ref('200'),
  copyMessage = ref('')
let toastTimer: ReturnType<typeof setTimeout> | undefined
const languages = [
  { id: 'curl', label: 'cURL' },
  { id: 'python', label: 'Python' },
  { id: 'javascript', label: 'Node.js' }
]
const workflow = [
  { title: '准备资源', detail: '查积分 / 素材 / 音色 / BGM', path: '/credits' },
  { title: '创建任务', detail: '提交口播和生成参数', path: '/tasks' },
  { title: '查询进度', detail: '保存任务号，等待终态', path: '/tasks/{taskId}' },
  { title: '下载成片', detail: '获取地址并保存视频', path: '/tasks/{taskId}/download' }
]
const activeId = computed(() => {
  try {
    return decodeURIComponent(route.hash.slice(1)) || 'overview'
  } catch {
    return 'overview'
  }
})
const operations = computed<Operation[]>(() =>
  Object.entries(catalog.value?.spec.paths || {}).flatMap(([path, definition]: [string, any]) =>
    Object.entries(definition)
      .filter(([method]) => ['get', 'post', 'put', 'delete', 'patch'].includes(method))
      .map(([method, definition]: [string, any]) => ({
        ...definition,
        path,
        method,
        id: `api-${method}-${path.replace(/[^a-z0-9]+/gi, '-')}`,
        summary: definition.summary || path
      }))
  )
)
const filteredOperations = computed(() => searchOperations(operations.value, query.value))
const groupedOperations = computed(() => {
  const order = [
    '接入与鉴权',
    '自动出片',
    '授权与连接',
    '视频上传',
    '发布任务',
    '免登录工具',
    '需后台权限'
  ]
  const names = [
    ...new Set<string>(filteredOperations.value.map((op) => op.tags?.[0] || '其它接口'))
  ]
  names.sort(
    (a, b) =>
      (order.includes(a) ? order.indexOf(a) : 99) - (order.includes(b) ? order.indexOf(b) : 99)
  )
  return names.map((name) => ({
    name,
    items: filteredOperations.value.filter((op) => (op.tags?.[0] || '其它接口') === name)
  }))
})
const activeGuide = computed(() =>
  catalog.value?.guides.find((g) => `guide-${g.id}` === activeId.value)
)
const activeOperation = computed(() => operations.value.find((op) => op.id === activeId.value))
const requestExample = computed(() =>
  activeOperation.value && catalog.value
    ? operationExample(
        catalog.value.spec,
        activeOperation.value.path,
        activeOperation.value.method,
        activeOperation.value
      )
    : {
        url: '',
        curl: '',
        python: '',
        javascript: '',
        signed: false,
        multipart: false,
        binary: false
      }
)
const securityLabel = computed(() =>
  (activeOperation.value?.security ?? catalog.value?.spec.security ?? []).length
    ? '账号 Token'
    : '无需登录'
)
const parameters = computed(() =>
  [
    ...(catalog.value?.spec.paths?.[activeOperation.value?.path || '']?.parameters || []),
    ...(activeOperation.value?.parameters || [])
  ].map((p: any) => resolveSchema(catalog.value!.spec, p))
)
const bodyContent = computed(
  () => resolveSchema(catalog.value?.spec, activeOperation.value?.requestBody).content || {}
)
const bodyMediaType = computed(() => Object.keys(bodyContent.value)[0] || 'application/json')
const bodySchema = computed(() =>
  resolveSchema(catalog.value?.spec, bodyContent.value[bodyMediaType.value]?.schema)
)
const bodyRows = computed(() =>
  catalog.value ? schemaRows(catalog.value.spec, bodySchema.value) : []
)
const responseMedia = computed(() =>
  catalog.value
    ? resolveSchema(catalog.value.spec, activeOperation.value?.responses?.[responseStatus.value])
        ?.content?.['application/json']
    : undefined
)
const responseExample = computed(() =>
  JSON.stringify(
    responseMedia.value?.example ??
      (Object.values(responseMedia.value?.examples || {}) as Array<{ value: unknown }>)[0]?.value ??
      (catalog.value ? sampleValue(catalog.value.spec, responseMedia.value?.schema) : {}),
    null,
    2
  )
)
const responseRows = computed(() =>
  catalog.value ? schemaRows(catalog.value.spec, responseMedia.value?.schema) : []
)

async function loadCatalog() {
  loading.value = true
  error.value = ''
  try {
    const response = await fetch('/api-reference/catalog.json', { credentials: 'omit' })
    if (!response.ok) throw Error(`HTTP ${response.status}`)
    const result = await response.json()
    if (!result.spec?.paths || !Array.isArray(result.guides)) throw Error('文档内容不完整')
    catalog.value = result
  } catch {
    error.value = '请检查网络后重试。也可以联系接入支持获取离线文档。'
  } finally {
    loading.value = false
  }
}
function selectTopic(id: string) {
  router.replace({ path: '/api-docs', hash: `#${id}` })
  navOpen.value = false
  pageRoot.value?.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior })
}
function selectByPath(suffix: string) {
  const operation = operations.value.find(
    (op) => op.path === `/admin-api/tk/automation/v1${suffix}`
  )
  if (operation) selectTopic(operation.id)
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    copyMessage.value = '已复制'
  } catch {
    copyMessage.value = '复制未完成，请选中代码手动复制'
  }
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    copyMessage.value = ''
  }, 2500)
}
function resize() {
  mobile.value = window.innerWidth < 850
}
watch(activeId, () => {
  responseStatus.value = '200'
})
onMounted(() => {
  resize()
  window.addEventListener('resize', resize)
  loadCatalog()
})
onBeforeUnmount(() => {
  clearTimeout(toastTimer)
  window.removeEventListener('resize', resize)
})
</script>

<style scoped src="./apiDocs.css"></style>
