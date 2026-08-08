const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')

const localeStore = read('src/store/modules/locale.ts')
const loginPage = read('src/views/Login/Login.vue')
const tkI18n = read('src/utils/tkI18n.ts')

const failures = []

const expectIncludes = (source, needle, message) => {
  if (!source.includes(needle)) {
    failures.push(message)
  }
}

expectIncludes(localeStore, "const DEFAULT_LOCALE: LocaleType = 'en'", 'Locale store must declare English as the default locale.')
expectIncludes(localeStore, 'TK_FORCE_EN_LOCALE_V1', 'Locale store must include the one-time English migration marker.')
expectIncludes(localeStore, 'resolveInitialLocale()', 'Locale store must resolve initial locale through the migration helper.')
expectIncludes(localeStore, "lang: initialLocale", 'Locale store state must use the resolved initial locale.')
expectIncludes(localeStore, "elLocale: elLocaleMap[initialLocale]", 'Element Plus locale must use the resolved initial locale.')

expectIncludes(loginPage, 'loginCopy', 'Login page must use reactive copy instead of hard-coded login text.')
expectIncludes(loginPage, 'useLocaleStore', 'Login page must follow the global locale store.')
expectIncludes(loginPage, '{{ loginCopy.heroTitle }}', 'Login page hero title must be locale-aware.')
expectIncludes(loginPage, '{{ loginCopy.panelTitle }}', 'Login page panel title must be locale-aware.')

const requiredTranslations = [
  'AI 混剪素材生产平台',
  '工作台登录',
  '安全连接',
  '素材库同步',
  '生成队列',
  '业务流水号',
  '素材库名称',
  '上传文件夹',
  '重新分析',
  '发布任务',
  '账号矩阵'
]

for (const text of requiredTranslations) {
  expectIncludes(tkI18n, text, `tkI18n must translate "${text}".`)
}

if (failures.length) {
  console.error('Default English locale check failed:')
  for (const failure of failures) {
    console.error(`- ${failure}`)
  }
  process.exit(1)
}

console.log('Default English locale check passed.')
