const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const read = (relativePath) => fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')

const dashboard = read('../src/views/tk/dashboard/index.vue')
const materialApi = read('../src/api/tk/material/index.ts')
const controller = read(
  '../../../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/material/TkMaterialLibraryController.java'
)
const mapper = read(
  '../../../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkMaterialLibraryMapper.java'
)
const dashboardService = read(
  '../../../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/dashboard/TkDashboardServiceImpl.java'
)

assert.match(dashboard, /filterable/, 'material library selector must support manual search')
assert.match(dashboard, /default-first-option/, 'manual search must support keyboard selection')
assert.match(
  dashboard,
  /:filter-placeholder="copy\.materialSearchPlaceholder"/,
  'material search must explain the input behavior'
)
assert.match(dashboard, /:no-match-text="copy\.materialNoMatch"/)
assert.match(dashboard, /:no-data-text="copy\.materialNoData"/)
assert.doesNotMatch(
  dashboard,
  /allow-create/,
  'manual input must not submit an arbitrary library name'
)
assert.match(dashboard, /createForm\.libraryId/, 'selected material library must remain ID-based')

assert.match(mapper, /default List<TkMaterialLibraryDO> selectAll\(TkUserScope scope\)/)
assert.doesNotMatch(
  mapper,
  /\.last\("LIMIT 5"\)/,
  'material library summary must not keep the five-item SQL limit'
)
assert.match(
  dashboardService,
  /libraryMapper\.selectAll\(scope\)/,
  'dashboard summary must use the complete material library query'
)
assert.match(
  dashboard,
  /const HOME_MATERIAL_LIMIT = 36/,
  'dashboard must define the six-row material overview limit'
)
assert.match(
  dashboard,
  /return libraries\.value\.slice\(0, HOME_MATERIAL_LIMIT\)\.map\(/,
  'dashboard material overview must render only the first six rows'
)
assert.match(
  dashboard,
  /libraries\.value = data\?\.libraries \|\| \[\]/,
  'dashboard must retain the complete library list for selectors and the full-list entry'
)
assert.doesNotMatch(materialApi, /getLibraryOptions:/)
assert.doesNotMatch(controller, /@GetMapping\("\/options"\)/)

console.log('material library all-items tests passed')
