const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')

const dashboardPath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const source = fs.readFileSync(dashboardPath, 'utf8')

const requiredSnippets = [
  'showRechargeDetail',
  'RECHARGE_POPUP_SESSION_KEY',
  'maybeShowRechargeDetailPopup',
  'sessionStorage',
  'creditBalance.value.lowBalance',
  'rechargeTierRows',
  'rechargeCostRows',
  'rechargeRuleRows',
  'copy.rechargeDetail',
  'copy.rechargeTiers',
  'copy.fullProcessCost',
  'copy.rechargeRules',
  '¥1,000',
  '8.0折',
  '¥4.00/积分',
  '250积分',
  '¥50,000',
  '6.0折',
  '¥3.00/积分',
  '16,667积分',
  '文案 + 视频 = 2积分/条',
  '充值后 7天内 如未使用任何积分，可申请全额退款；超过7天视为认可服务，不再退费'
]

for (const snippet of requiredSnippets) {
  assert(
    source.includes(snippet),
    `dashboard recharge detail is missing required snippet: ${snippet}`
  )
}

const forbiddenSnippets = ['handleContactRecharge', 'copyRechargeEmail', 'mailto:']
for (const snippet of forbiddenSnippets) {
  assert(
    !source.includes(snippet),
    `dashboard recharge detail must not include forbidden snippet: ${snippet}`
  )
}

console.log('dashboard recharge detail checks passed')
