SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `tk_api_key_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置编号',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  `provider` varchar(32) NOT NULL COMMENT '服务商：GEMINI/DASHSCOPE',
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` text NULL COMMENT '配置值',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_api_key_config_provider_key` (`tenant_id`, `provider`, `config_key`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 外部服务密钥配置';

ALTER TABLE `tk_api_key_config`
  MODIFY COLUMN `config_value` text NULL COMMENT '配置值';

INSERT INTO `tk_api_key_config` (`tenant_id`, `provider`, `config_key`, `config_value`, `remark`, `status`)
VALUES
  (1, 'GEMINI', 'api-key', '', 'Gemini 文案生成 API Key，留空时使用环境变量 GEMINI_API_KEY', 0),
  (1, 'GEMINI', 'base-url', 'https://yunwu.ai/v1', 'Gemini 文案生成接口基础地址', 0),
  (1, 'GEMINI', 'text-model', 'gemini-3.1-flash-lite-preview', 'Gemini 文案生成模型', 0),
  (1, 'GEMINI', 'api-format', 'openai', 'Gemini 调用协议：gemini/openai', 0),
  (1, 'GEMINI', 'timeout-seconds', '90', 'Gemini 请求超时时间（秒）', 0),
  (1, 'GEMINI', 'retry-count', '2', 'Gemini 临时失败重试次数', 0),
  (1, 'GEMINI', 'retry-delay-ms', '1500', 'Gemini 重试退避起始间隔（毫秒）', 0),
  (1, 'GEMINI', 'analysis-prompt', '你是 TikTok 跨境电商短视频策略分析师、爆款拆解专家和结构化内容生成模型。\n你的任务是：基于随请求提供的对标视频关键帧、真实视频元数据和链接信息，分析视频内容，提炼可用于 TK 自动混剪 SaaS 系统落库的数据，并生成可用于后续混剪和发布的文案标题方案。\n严格要求：\n1. 只依据关键帧画面、真实视频元数据、可见字幕、可见商品、可见场景进行分析。\n2. 严禁根据素材库名称、素材库类目、素材库场景、素材库标签猜测商品、卖点、人群或视频结构。\n3. 素材库相关字段只能用于后续文案适配，不能作为商品识别证据。\n4. 如果画面或元数据无法确认具体商品，productName 必须写成“未识别具体商品”。\n5. 核心卖点、目标人群、使用场景、视频结构、文案方案必须能被画面或元数据支撑。\n6. 不要输出 Markdown，不要解释，不要补充多余文本，只输出合法 JSON。\n7. 所有表达要适合 TikTok 电商短视频，短句化、强钩子、适合字幕展示。\n8. 不得生成夸大、绝对化、医疗功效、违规承诺或无法从视频证明的内容。\n输出 JSON 结构必须如下：\n{\n  "productName": "产品名；无法确认时写未识别具体商品",\n  "videoDuration": 32,\n  "publishTime": "无法从元数据确认时返回空字符串",\n  "coreSellingPoints": ["核心卖点1", "核心卖点2", "核心卖点3"],\n  "targetAudience": ["目标人群1", "目标人群2"],\n  "usageScenarios": ["使用场景1", "使用场景2"],\n  "videoStructure": ["开头钩子", "产品展示", "功能演示", "场景证明", "行动号召"],\n  "sellingPoints": [\n    {"title": "卖点标题", "desc": "基于画面可见内容提炼的卖点描述", "count": 3, "badge": "核心卖点"}\n  ],\n  "scriptOptions": [\n    {"title": "适合 TikTok 发布的短视频标题", "points": "卖点A｜卖点B", "displayTitleZh": "后台展示中文标题", "displayPointsZh": "后台展示中文卖点", "estimatedConversionRate": 8.92, "conversionLevel": "高", "scriptText": "完整口播文案，短句化，适合字幕和配音", "displayScriptZh": "后台展示中文口播文案"}\n  ]\n}\n文案方案要求：\n1. scriptOptions 必须输出 12 条。\n2. estimatedConversionRate 范围必须在 4.80 到 9.50 之间。\n3. 前 2 条 conversionLevel 为“高”，其余为“中”或“低”。\n4. 每条 title 不超过 60 个中文字符或等量目标语言长度。\n5. scriptText 必须适合 20-60 秒短视频口播，包含：前 3 秒钩子、卖点展开、场景证明、轻行动号召。\n6. 每条文案角度必须不同，避免同质化。\n\n对标链接：{}\n解析到的视频地址：{}\n真实视频时长：{}秒\n真实视频分辨率：{}\n关键帧时间点：{}\n素材库名称（仅用于文案适配，不能作为识别依据）：{}\n素材库类目（仅用于文案适配，不能作为识别依据）：{}\n素材库场景（仅用于文案适配，不能作为识别依据）：{}\n素材库标签（仅用于文案适配，不能作为识别依据）：{}\n用户参考时长：{}秒\n', 'Gemini 对标视频分析提示词模板', 0),
  (1, 'GEMINI', 'script-regeneration-prompt', '你是 TikTok 跨境电商短视频文案策划专家。\n你的任务是：只基于系统中已保存的对标分析结果，重新生成一组新的文案标题方案，用于 TK 自动混剪 SaaS 的文案换一批功能。\n严格要求：\n1. 不要重新分析视频。\n2. 不要改写 productName、核心卖点、目标人群、使用场景、视频结构和卖点细节。\n3. 必须围绕已保存分析结果生成新标题和新口播脚本。\n4. 标题角度必须与上一批明显不同，避免重复表达。\n5. 不要输出 Markdown，不要解释，只输出合法 JSON。\n6. 不得生成夸大、绝对化、医疗功效、违规承诺或分析结果中没有依据的内容。\n输出 JSON 结构必须如下：\n{\n  "scriptOptions": [\n    {"title": "新的 TikTok 短视频标题", "points": "卖点A｜卖点B", "displayTitleZh": "后台展示中文标题", "displayPointsZh": "后台展示中文卖点", "estimatedConversionRate": 8.92, "conversionLevel": "高", "scriptText": "完整口播文案，短句化，适合字幕和配音", "displayScriptZh": "后台展示中文口播文案"}\n  ]\n}\n文案方案要求：\n1. scriptOptions 必须输出 12 条。\n2. estimatedConversionRate 范围必须在 4.80 到 9.50 之间。\n3. 前 2 条 conversionLevel 为“高”，其余为“中”或“低”。\n4. 每条 title 不超过 60 个中文字符或等量目标语言长度。\n5. 每条 scriptText 必须包含：前 3 秒钩子、卖点展开、场景证明、轻行动号召。\n6. 12 条方案要分别覆盖不同角度，例如：痛点型、测评型、反差型、场景型、礼物型、限时型、清单型、对比型。\n\n对标链接：{}\n产品名：{}\n视频时长：{}秒\n核心卖点：{}\n目标人群：{}\n使用场景：{}\n视频结构：{}\n卖点细节 JSON：{}\n素材库名称：{}\n素材库类目：{}\n素材库场景：{}\n素材库标签：{}\n', 'Gemini 重新生成文案方案提示词模板', 0),
  (1, 'GEMINI', 'generation-script-prompt', '你是 TikTok 跨境电商短视频编导、口播脚本策划和混剪节奏设计师。\n你的任务是：基于用户选择的对标视频链接、素材库信息和目标成片时长，生成一条可直接用于 AI 配音、字幕切分和自动混剪的带货脚本。\n严格要求：\n1. 输出纯文案，不要 Markdown，不要解释，不要标题前缀。\n2. 文案必须适合口播配音和逐句字幕。\n3. 每一句尽量短，控制在 8-18 个中文字符或等量目标语言长度。\n4. 前 3 秒必须是强钩子，用来承接用户上传的黄金三秒视频。\n5. 后续结构必须包含：卖点展开、场景证明、信任背书、行动号召。\n6. 文案节奏要适合短视频混剪，每 2-4 秒一个信息点。\n7. 不要生成虚假价格、虚假折扣、绝对化承诺、医疗功效或平台违规表达。\n8. 如果商品信息不足，使用更稳妥的泛化表达，不要编造具体功能。\n9. 默认使用中文；如果后续语言指令要求其他目标语言，以后续语言指令为准。\n10. 不要出现“以下是”“这是一段脚本”等说明性文字。\n内容结构：\n- 第 1 段：黄金三秒钩子，制造停留理由。\n- 第 2 段：指出用户痛点或使用场景。\n- 第 3 段：展示核心卖点。\n- 第 4 段：强化场景证明或使用体验。\n- 第 5 段：轻量行动号召，适合 TikTok 发布。\n\n对标视频链接：{}\n素材库：{}\n类目：{}\n场景：{}\n标签：{}\n目标成片时长：{}秒\n素材规则：黄金三秒开头固定使用用户上传视频；后续素材每段裁剪{}秒；文案需要适配配音和逐句字幕。', 'Gemini 生成任务兜底文案提示词模板', 0),
  (1, 'DASHSCOPE', 'api-key', '', 'DashScope API Key，留空时使用环境变量 DASHSCOPE_API_KEY', 0),
  (1, 'DASHSCOPE', 'video-api-key', '', '百炼视频理解专用 API Key，留空时使用环境变量 DASHSCOPE_VIDEO_API_KEY', 0),
  (1, 'DASHSCOPE', 'tts-url', 'https://dashscope.aliyuncs.com/api/v1/services/audio/tts/SpeechSynthesizer', 'DashScope 非流式语音合成地址', 0),
  (1, 'DASHSCOPE', 'tts-model', 'cosyvoice-v3.5-plus', 'DashScope TTS 模型', 0),
  (1, 'DASHSCOPE', 'voice', '', 'DashScope 默认复刻音色 ID；任务 voiceCode 为空时使用', 0),
  (1, 'DASHSCOPE', 'format', 'mp3', '音频格式', 0),
  (1, 'DASHSCOPE', 'language', 'auto', '默认语言提示，auto 按文案自动识别', 0),
  (1, 'DASHSCOPE', 'instruction', '请以自然、清晰的语气朗读,不要有换气声、吸气声、呼吸声或任何气口,句子之间不要停顿。语速为1.1倍，文字中出现的, '' 不要进行停顿', 'DashScope 朗读指令', 0),
  (1, 'DASHSCOPE', 'workspace-id', '', '百炼华北2北京业务空间 ID', 0),
  (1, 'DASHSCOPE', 'video-model', 'qwen3.7-plus', '百炼视频理解模型', 0),
  (1, 'DASHSCOPE', 'video-fps', '2', '视频理解抽帧频率', 0),
  (1, 'DASHSCOPE', 'video-timeout-seconds', '300', '视频理解超时秒数', 0),
  (1, 'DASHSCOPE', 'video-enable-thinking', 'false', '视频理解思考模式', 0),
  (1, 'DASHSCOPE', 'video-temperature', '0.2', '视频理解温度', 0)
ON DUPLICATE KEY UPDATE
  `config_value` = IF(VALUES(`provider`) = 'GEMINI'
    AND VALUES(`config_key`) IN ('analysis-prompt', 'script-regeneration-prompt', 'generation-script-prompt'),
    VALUES(`config_value`), `config_value`),
  `remark` = VALUES(`remark`);
