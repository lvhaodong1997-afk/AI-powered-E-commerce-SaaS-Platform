const fs = require('fs')
const path = require('path')

const materialPath = path.resolve(__dirname, '../src/views/tk/material-library/index.vue')
const source = fs.readFileSync(materialPath, 'utf8')

const checks = [
  {
    name: 'folder upload has a separate 200 video limit',
    pass: source.includes('const MAX_FOLDER_UPLOAD_COUNT = 200')
  },
  {
    name: 'normal file upload still keeps 10 file limit',
    pass: source.includes('const MAX_BATCH_UPLOAD_COUNT = 10')
  },
  {
    name: 'folder picker uses webkitdirectory without replacing el-upload',
    pass: source.includes('webkitdirectory') && source.includes('handleFolderFileChange') && source.includes('<el-upload')
  },
  {
    name: 'folder files reuse the existing upload queue',
    pass: source.includes('appendFolderFilesToUploadQueue') && source.includes('uploadSingleFile(item)')
  },
  {
    name: 'folder scan reports ignored and oversized files',
    pass:
      source.includes('folderUploadSummary') &&
      source.includes('ignoredCount') &&
      source.includes('oversizedCount')
  }
]

const failed = checks.filter((check) => !check.pass)

if (failed.length) {
  console.error('Material folder upload checks failed:')
  failed.forEach((check) => console.error(`- ${check.name}`))
  process.exit(1)
}

console.log(`Material folder upload checks passed: ${checks.length}/${checks.length}`)
