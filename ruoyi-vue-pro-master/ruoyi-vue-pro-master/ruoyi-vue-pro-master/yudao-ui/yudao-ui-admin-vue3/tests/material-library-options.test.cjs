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
  /return libraries\.value\.map\(/,
  'dashboard material overview must render every returned library'
)
assert.doesNotMatch(materialApi, /getLibraryOptions:/)
assert.doesNotMatch(controller, /@GetMapping\("\/options"\)/)

console.log('material library all-items tests passed')
