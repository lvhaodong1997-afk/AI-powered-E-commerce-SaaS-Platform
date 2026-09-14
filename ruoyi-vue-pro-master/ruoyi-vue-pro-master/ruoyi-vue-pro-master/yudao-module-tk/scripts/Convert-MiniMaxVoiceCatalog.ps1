param(
    [Parameter(Mandatory = $true)]
    [string]$SourceSql,
    [Parameter(Mandatory = $true)]
    [string]$OutputSql
)

$ErrorActionPreference = 'Stop'
$source = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $SourceSql), [System.Text.Encoding]::UTF8)
$voiceBlock = [regex]::Match($source, '(?s)DELETE FROM sys_dict_data WHERE dict_type = ''ai_tts_voice'';.*?VALUES\s*(.*?);\s*INSERT INTO sys_dict_type.*?ai_tts_config')
if (-not $voiceBlock.Success) {
    throw 'Unable to locate ai_tts_voice VALUES block'
}

$tuplePattern = [regex]'(?m)^\s*\((?<sort>\d+),\s*''(?<label>(?:''''|[^''])*)'',\s*''(?<value>(?:''''|[^''])*)'',\s*''ai_tts_voice'',\s*'''',\s*'''',\s*''(?<default>[YN])'',\s*''(?<status>[01])'',\s*''admin'',\s*NOW\(\),\s*'''',\s*NULL,\s*''(?<remark>(?:''''|[^''])*)''\)[,;]?\s*$'
$records = $tuplePattern.Matches($voiceBlock.Groups[1].Value)
if ($records.Count -ne 303) {
    throw "Expected 303 voice records, found $($records.Count)"
}

function Decode-Sql([string]$value) { return $value.Replace("''", "'") }
function Encode-Sql([string]$value) { return $value.Replace("'", "''") }

$lines = [System.Collections.Generic.List[string]]::new()
$genderCount = 0
$defaultVoiceIds = [System.Collections.Generic.List[string]]::new()
$unencodedPreviewUrls = 0
$doubleEncodedPreviewUrls = 0
$lines.Add('-- Generated deterministically by scripts/Convert-MiniMaxVoiceCatalog.ps1.')
$lines.Add('-- Adds missing MiniMax catalog/config rows only; existing curated rows are authoritative.')
$lines.Add('')
$lines.Add('CREATE TABLE IF NOT EXISTS `tk_voice_favorite` (')
$lines.Add('  `id` bigint NOT NULL AUTO_INCREMENT COMMENT ''收藏编号'',')
$lines.Add('  `tenant_id` bigint NOT NULL COMMENT ''租户编号'',')
$lines.Add('  `user_id` bigint NOT NULL COMMENT ''用户编号'',')
$lines.Add('  `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT ''音色供应商'',')
$lines.Add('  `voice_code` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT ''供应商音色编码'',')
$lines.Add('  `creator` varchar(64) DEFAULT '''' COMMENT ''创建者'',')
$lines.Add('  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',')
$lines.Add('  `updater` varchar(64) DEFAULT '''' COMMENT ''更新者'',')
$lines.Add('  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',')
$lines.Add('  `deleted` bit(1) NOT NULL DEFAULT b''0'' COMMENT ''是否删除'',')
$lines.Add('  PRIMARY KEY (`id`),')
$lines.Add('  UNIQUE KEY `uk_tk_voice_favorite_scope` (`tenant_id`, `user_id`, `provider`, `voice_code`),')
$lines.Add('  KEY `idx_tk_voice_favorite_user` (`tenant_id`, `user_id`, `provider`)')
$lines.Add(') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT=''TK 用户音色收藏'';')
$lines.Add('')
$maxRemarkLength = 0
foreach ($record in $records) {
    $metadata = (Decode-Sql $record.Groups['remark'].Value) | ConvertFrom-Json
    $metadata | Add-Member -NotePropertyName isDefault -NotePropertyValue ($record.Groups['default'].Value -eq 'Y') -Force
    if (-not [string]::IsNullOrWhiteSpace([string]$metadata.gender)) { $genderCount++ }
    if ($record.Groups['default'].Value -eq 'Y') { $defaultVoiceIds.Add([string]$metadata.voiceId) }
    if ([string]$metadata.previewUrl -match ' ') { $unencodedPreviewUrls++ }
    if ([string]$metadata.previewUrl -match '%25(?:20|28|29)') { $doubleEncodedPreviewUrls++ }
    $length = ($metadata | ConvertTo-Json -Compress -Depth 10).Length
    if ($length -gt $maxRemarkLength) { $maxRemarkLength = $length }
}
$lines.Add("SET @dict_remark_needs_text := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'system_dict_data' AND column_name = 'remark' AND data_type IN ('char', 'varchar') AND character_maximum_length < $maxRemarkLength);")
$lines.Add('SET @dict_remark_sql := IF(@dict_remark_needs_text > 0, ''ALTER TABLE `system_dict_data` MODIFY COLUMN `remark` text DEFAULT NULL COMMENT ''''备注'''' '', ''SELECT ''''system_dict_data.remark already text'''' '');')
$lines.Add('PREPARE dict_remark_stmt FROM @dict_remark_sql;')
$lines.Add('EXECUTE dict_remark_stmt;')
$lines.Add('DEALLOCATE PREPARE dict_remark_stmt;')
$lines.Add('')

foreach ($type in @(
    @{ Name = 'AI配音音色'; Type = 'ai_tts_voice'; Remark = 'MiniMax系统音色；国家仅用于展示和搜索' },
    @{ Name = 'AI配音参数'; Type = 'ai_tts_config'; Remark = 'MiniMax同步语音合成默认参数' }
)) {
    $lines.Add("INSERT INTO ``system_dict_type`` (``name``, ``type``, ``status``, ``remark``, ``creator``, ``create_time``, ``updater``, ``update_time``)")
    $lines.Add("SELECT '$($type.Name)', '$($type.Type)', 0, '$($type.Remark)', '0', NOW(), '0', NOW()")
    $lines.Add("WHERE NOT EXISTS (SELECT 1 FROM ``system_dict_type`` WHERE ``type`` = '$($type.Type)');")
    $lines.Add('')
}

foreach ($record in $records) {
    $sort = $record.Groups['sort'].Value
    $label = Decode-Sql $record.Groups['label'].Value
    $value = Decode-Sql $record.Groups['value'].Value
    $status = $record.Groups['status'].Value
    $metadata = (Decode-Sql $record.Groups['remark'].Value) | ConvertFrom-Json
    $metadata | Add-Member -NotePropertyName isDefault -NotePropertyValue ($record.Groups['default'].Value -eq 'Y') -Force
    $remark = $metadata | ConvertTo-Json -Compress -Depth 10
    $escapedLabel = Encode-Sql $label
    $escapedValue = Encode-Sql $value
    $escapedRemark = Encode-Sql $remark
    $lines.Add("-- VOICE_RECORD $sort $escapedValue")
    $lines.Add("SET @voice_value := '$escapedValue';")
    $lines.Add('INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)')
    $lines.Add("SELECT $sort, '$escapedLabel', @voice_value, 'ai_tts_voice', $status, '', '', '$escapedRemark', '0', NOW(), '0', NOW()")
    $lines.Add("WHERE NOT EXISTS (SELECT 1 FROM ``system_dict_data`` WHERE ``dict_type`` = 'ai_tts_voice' AND CAST(``value`` AS BINARY) = CAST(@voice_value AS BINARY));")
    $lines.Add('')
}

$configBlock = [regex]::Match($source, '(?s)DELETE FROM sys_dict_data WHERE dict_type = ''ai_tts_config'';.*?VALUES\s*(.*?);\s*$')
if (-not $configBlock.Success) { throw 'Unable to locate ai_tts_config VALUES block' }
$configPattern = [regex]'(?m)^\s*\((?<sort>\d+),\s*''(?<label>(?:''''|[^''])*)'',\s*''(?<value>(?:''''|[^''])*)'',\s*''ai_tts_config'',.*?,\s*''(?<remark>\{.*\})''\)[,;]?\s*$'
$configs = $configPattern.Matches($configBlock.Groups[1].Value)
if ($configs.Count -ne 13) { throw "Expected 13 config records, found $($configs.Count)" }
foreach ($record in $configs) {
    $sort = $record.Groups['sort'].Value
    $label = Encode-Sql (Decode-Sql $record.Groups['label'].Value)
    $value = Encode-Sql (Decode-Sql $record.Groups['value'].Value)
    $remark = Encode-Sql (Decode-Sql $record.Groups['remark'].Value)
    $lines.Add("SET @config_value := '$value';")
    $lines.Add('INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)')
    $lines.Add("SELECT $sort, '$label', @config_value, 'ai_tts_config', 0, '', '', '$remark', '0', NOW(), '0', NOW()")
    $lines.Add("WHERE NOT EXISTS (SELECT 1 FROM ``system_dict_data`` WHERE ``dict_type`` = 'ai_tts_config' AND CAST(``value`` AS BINARY) = CAST(@config_value AS BINARY));")
    $lines.Add('')
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputSql)
[bool]$validDefaults = $defaultVoiceIds.Count -eq 1
if (-not $validDefaults) { throw "Expected exactly one default voice, found $($defaultVoiceIds.Count)" }
if ($unencodedPreviewUrls -ne 0) { throw "Found $unencodedPreviewUrls preview URLs containing unencoded spaces" }
if ($doubleEncodedPreviewUrls -ne 0) { throw "Found $doubleEncodedPreviewUrls double-encoded preview URLs" }
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolvedOutput)) | Out-Null
[System.IO.File]::WriteAllLines($resolvedOutput, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Generated $resolvedOutput"
Write-Output "sourceVoices=$($records.Count) configs=$($configs.Count) genderPresent=$genderCount genderMissing=$($records.Count - $genderCount) defaultVoiceId=$($defaultVoiceIds[0]) unencodedPreviewUrls=$unencodedPreviewUrls doubleEncodedPreviewUrls=$doubleEncodedPreviewUrls"
