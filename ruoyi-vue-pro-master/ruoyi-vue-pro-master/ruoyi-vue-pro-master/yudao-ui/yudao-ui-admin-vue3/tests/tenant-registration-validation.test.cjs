const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const { createRequire } = require('node:module')
const ts = require('typescript')
const Schema = createRequire(require.resolve('element-plus'))('async-validator').default

// Evaluate the actual form field/rule declarations, then use Element Plus's validator.
const source = fs.readFileSync(path.join(__dirname, '../src/views/Login/components/RegisterForm.vue'), 'utf8')
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
const ast = ts.createSourceFile('RegisterForm.ts', script, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS)
const selected = ast.statements.filter((statement) => ts.isVariableStatement(statement) &&
  statement.declarationList.declarations.some((declaration) =>
    ['text', 'fields', 'requiredRule', 'rules'].includes(declaration.name.getText(ast))))
const context = { exports: {}, computed: (fn) => fn(), locale: { getCurrentLocale: { lang: 'en' } } }
vm.runInNewContext(ts.transpileModule(selected.map((node) => node.getText(ast)).join('\n') +
  '\nexports.fields = fields; exports.rules = rules;', {
  compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS }
}).outputText, context)

test('workspace input caps at the production thirty-character column', () => {
  assert.equal(context.exports.fields.find((field) => field.key === 'tenantName').max, 30)
})

test('workspace validation accepts thirty characters and rejects thirty-one', async () => {
  const validator = new Schema({ tenantName: context.exports.rules.tenantName })
  await validator.validate({ tenantName: '租'.repeat(30) })
  await assert.rejects(validator.validate({ tenantName: '租'.repeat(31) }))
})

test('password input describes the six-to-sixteen-character registration policy', () => {
  const field = context.exports.fields.find((field) => field.key === 'password')
  assert.equal(field.max, 16)
  assert.equal(field.label, 'Password (6–16 characters)')
})

test('password validation accepts six and sixteen characters and rejects shorter or longer values', async () => {
  const validator = new Schema({ password: context.exports.rules.password })
  for (const length of [4, 5, 17]) {
    await assert.rejects(validator.validate({ password: 'p'.repeat(length) }))
  }
  for (const length of [6, 16]) {
    await validator.validate({ password: 'p'.repeat(length) })
  }
})
