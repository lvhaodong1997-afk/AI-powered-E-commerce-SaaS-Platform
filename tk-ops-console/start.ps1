$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
  throw "Node.js 未安装或不在 PATH 中，请先安装 Node.js 20+。"
}

if (-not (Test-Path "node_modules")) {
  npm install
}

npm start
