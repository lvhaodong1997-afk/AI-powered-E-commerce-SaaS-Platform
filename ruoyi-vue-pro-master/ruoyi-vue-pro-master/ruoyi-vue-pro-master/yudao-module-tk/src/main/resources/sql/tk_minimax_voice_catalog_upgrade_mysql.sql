-- Generated deterministically by scripts/Convert-MiniMaxVoiceCatalog.ps1.
-- Adds missing MiniMax catalog/config rows only; existing curated rows are authoritative.

CREATE TABLE IF NOT EXISTS `tk_voice_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '收藏编号',
  `tenant_id` bigint NOT NULL COMMENT '租户编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '音色供应商',
  `voice_code` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT '供应商音色编码',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tk_voice_favorite_scope` (`tenant_id`, `user_id`, `provider`, `voice_code`),
  KEY `idx_tk_voice_favorite_user` (`tenant_id`, `user_id`, `provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='TK 用户音色收藏';

SET @dict_remark_needs_text := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'system_dict_data' AND column_name = 'remark' AND data_type IN ('char', 'varchar') AND character_maximum_length < 298);
SET @dict_remark_sql := IF(@dict_remark_needs_text > 0, 'ALTER TABLE `system_dict_data` MODIFY COLUMN `remark` text DEFAULT NULL COMMENT ''备注'' ', 'SELECT ''system_dict_data.remark already text'' ');
PREPARE dict_remark_stmt FROM @dict_remark_sql;
EXECUTE dict_remark_stmt;
DEALLOCATE PREPARE dict_remark_stmt;

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 'AI配音音色', 'ai_tts_voice', 0, 'MiniMax系统音色；国家仅用于展示和搜索', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'ai_tts_voice');

INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 'AI配音参数', 'ai_tts_config', 0, 'MiniMax同步语音合成默认参数', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'ai_tts_config');

-- VOICE_RECORD 10 Arrogant_Miss
SET @voice_value := 'Arrogant_Miss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 10, '嚣张小姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Arrogant_Miss","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Arrogant_Miss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 20 badao_shaoye
SET @voice_value := 'badao_shaoye';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 20, '霸道少爷', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"badao_shaoye","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/badao_shaoye.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 30 bingjiao_didi
SET @voice_value := 'bingjiao_didi';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 30, '病娇弟弟', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"bingjiao_didi","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/bingjiao_didi.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 40 cartoon_pig
SET @voice_value := 'cartoon_pig';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 40, '卡通猪小琪', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"cartoon_pig","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/cartoon_pig.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 50 Chinese (Mandarin)_Crisp_Girl
SET @voice_value := 'Chinese (Mandarin)_Crisp_Girl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 50, '清脆少女', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Crisp_Girl","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Crisp_Girl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 60 Chinese (Mandarin)_Cute_Spirit
SET @voice_value := 'Chinese (Mandarin)_Cute_Spirit';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 60, '憨憨萌兽', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Cute_Spirit","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Cute_Spirit.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 70 Chinese (Mandarin)_Gentle_Senior
SET @voice_value := 'Chinese (Mandarin)_Gentle_Senior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 70, '温柔学姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Gentle_Senior","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Gentle_Senior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 80 Chinese (Mandarin)_Gentle_Youth
SET @voice_value := 'Chinese (Mandarin)_Gentle_Youth';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 80, '温润青年', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Gentle_Youth","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Gentle_Youth.mp3","isDefault":true}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 90 Chinese (Mandarin)_Gentleman
SET @voice_value := 'Chinese (Mandarin)_Gentleman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 90, '温润男声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Gentleman","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Gentleman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 100 Chinese (Mandarin)_HK_Flight_Attendant
SET @voice_value := 'Chinese (Mandarin)_HK_Flight_Attendant';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 100, '港普空姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_HK_Flight_Attendant","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_HK_Flight_Attendant.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 110 Chinese (Mandarin)_Humorous_Elder
SET @voice_value := 'Chinese (Mandarin)_Humorous_Elder';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 110, '搞笑大爷', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Humorous_Elder","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Humorous_Elder.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 120 Chinese (Mandarin)_Kind-hearted_Antie
SET @voice_value := 'Chinese (Mandarin)_Kind-hearted_Antie';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 120, '热心大婶', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Kind-hearted_Antie","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Kind-hearted_Antie.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 130 Chinese (Mandarin)_Kind-hearted_Elder
SET @voice_value := 'Chinese (Mandarin)_Kind-hearted_Elder';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 130, '花甲奶奶', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Kind-hearted_Elder","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Kind-hearted_Elder.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 140 Chinese (Mandarin)_Lyrical_Voice
SET @voice_value := 'Chinese (Mandarin)_Lyrical_Voice';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 140, '抒情男声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Lyrical_Voice","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Lyrical_Voice.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 150 Chinese (Mandarin)_Male_Announcer
SET @voice_value := 'Chinese (Mandarin)_Male_Announcer';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 150, '播报男声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Male_Announcer","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Male_Announcer.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 160 Chinese (Mandarin)_Mature_Woman
SET @voice_value := 'Chinese (Mandarin)_Mature_Woman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 160, '傲娇御姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Mature_Woman","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Mature_Woman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 170 Chinese (Mandarin)_News_Anchor
SET @voice_value := 'Chinese (Mandarin)_News_Anchor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 170, '新闻女声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_News_Anchor","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_News_Anchor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 180 Chinese (Mandarin)_Pure-hearted_Boy
SET @voice_value := 'Chinese (Mandarin)_Pure-hearted_Boy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 180, '清澈邻家弟弟', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Pure-hearted_Boy","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Pure-hearted_Boy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 190 Chinese (Mandarin)_Radio_Host
SET @voice_value := 'Chinese (Mandarin)_Radio_Host';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 190, '电台男主播', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Radio_Host","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Radio_Host.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 200 Chinese (Mandarin)_Reliable_Executive
SET @voice_value := 'Chinese (Mandarin)_Reliable_Executive';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 200, '沉稳高管', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Reliable_Executive","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Reliable_Executive.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 210 Chinese (Mandarin)_Sincere_Adult
SET @voice_value := 'Chinese (Mandarin)_Sincere_Adult';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 210, '真诚青年', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Sincere_Adult","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Sincere_Adult.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 220 Chinese (Mandarin)_Soft_Girl
SET @voice_value := 'Chinese (Mandarin)_Soft_Girl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 220, '柔和少女', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Soft_Girl","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Soft_Girl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 230 Chinese (Mandarin)_Southern_Young_Man
SET @voice_value := 'Chinese (Mandarin)_Southern_Young_Man';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 230, '南方小哥', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Southern_Young_Man","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Southern_Young_Man.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 240 Chinese (Mandarin)_Straightforward_Boy
SET @voice_value := 'Chinese (Mandarin)_Straightforward_Boy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 240, '率真弟弟', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Straightforward_Boy","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Straightforward_Boy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 250 Chinese (Mandarin)_Stubborn_Friend
SET @voice_value := 'Chinese (Mandarin)_Stubborn_Friend';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 250, '嘴硬竹马', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Stubborn_Friend","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Stubborn_Friend.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 260 Chinese (Mandarin)_Sweet_Lady
SET @voice_value := 'Chinese (Mandarin)_Sweet_Lady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 260, '甜美女声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Sweet_Lady","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Sweet_Lady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 270 Chinese (Mandarin)_Unrestrained_Young_Man
SET @voice_value := 'Chinese (Mandarin)_Unrestrained_Young_Man';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 270, '不羁青年', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Unrestrained_Young_Man","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Unrestrained_Young_Man.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 280 Chinese (Mandarin)_Warm_Bestie
SET @voice_value := 'Chinese (Mandarin)_Warm_Bestie';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 280, '温暖闺蜜', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Warm_Bestie","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Warm_Bestie.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 290 Chinese (Mandarin)_Warm_Girl
SET @voice_value := 'Chinese (Mandarin)_Warm_Girl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 290, '温暖少女', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Warm_Girl","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Warm_Girl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 300 Chinese (Mandarin)_Wise_Women
SET @voice_value := 'Chinese (Mandarin)_Wise_Women';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 300, '阅历姐姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Chinese (Mandarin)_Wise_Women","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Chinese%20(Mandarin)_Wise_Women.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 310 chunzhen_xuedi
SET @voice_value := 'chunzhen_xuedi';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 310, '纯真学弟', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"chunzhen_xuedi","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/chunzhen_xuedi.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 320 clever_boy
SET @voice_value := 'clever_boy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 320, '聪明男童', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"clever_boy","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/clever_boy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 330 cute_boy
SET @voice_value := 'cute_boy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 330, '可爱男童', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"cute_boy","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/cute_boy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 340 danya_xuejie
SET @voice_value := 'danya_xuejie';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 340, '淡雅学姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"danya_xuejie","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/danya_xuejie.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 350 diadia_xuemei
SET @voice_value := 'diadia_xuemei';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 350, '嗲嗲学妹', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"diadia_xuemei","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/diadia_xuemei.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 360 female-chengshu-jingpin
SET @voice_value := 'female-chengshu-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 360, '成熟女性音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-chengshu-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-chengshu-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 370 female-shaonv-jingpin
SET @voice_value := 'female-shaonv-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 370, '少女音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-shaonv-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-shaonv-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 380 female-tianmei-jingpin
SET @voice_value := 'female-tianmei-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 380, '甜美女性音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-tianmei-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-tianmei-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 390 female-yujie-jingpin
SET @voice_value := 'female-yujie-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 390, '御姐音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-yujie-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-yujie-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 400 junlang_nanyou
SET @voice_value := 'junlang_nanyou';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 400, '俊朗男友', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"junlang_nanyou","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/junlang_nanyou.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 410 lengdan_xiongzhang
SET @voice_value := 'lengdan_xiongzhang';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 410, '冷淡学长', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"lengdan_xiongzhang","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/lengdan_xiongzhang.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 420 lovely_girl
SET @voice_value := 'lovely_girl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 420, '萌萌女童', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"lovely_girl","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/lovely_girl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 430 male-qn-badao-jingpin
SET @voice_value := 'male-qn-badao-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 430, '霸道青年音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-badao-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-badao-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 440 male-qn-daxuesheng-jingpin
SET @voice_value := 'male-qn-daxuesheng-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 440, '青年大学生音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-daxuesheng-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-daxuesheng-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 450 male-qn-jingying-jingpin
SET @voice_value := 'male-qn-jingying-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 450, '精英青年音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-jingying-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-jingying-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 460 male-qn-qingse-jingpin
SET @voice_value := 'male-qn-qingse-jingpin';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 460, '青涩青年音色-beta', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-qingse-jingpin","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-qingse-jingpin.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 470 qiaopi_mengmei
SET @voice_value := 'qiaopi_mengmei';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 470, '俏皮萌妹', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"qiaopi_mengmei","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/qiaopi_mengmei.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 480 Robot_Armor
SET @voice_value := 'Robot_Armor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 480, '机械战甲', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Robot_Armor","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Robot_Armor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 490 tianxin_xiaoling
SET @voice_value := 'tianxin_xiaoling';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 490, '甜心小玲', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"tianxin_xiaoling","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/tianxin_xiaoling.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 500 wumei_yujie
SET @voice_value := 'wumei_yujie';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 500, '妩媚御姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"wumei_yujie","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/wumei_yujie.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 510 vod-clone-jieshuonan1
SET @voice_value := 'vod-clone-jieshuonan1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 510, '专业解说男', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-clone-jieshuonan1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-clone-jieshuonan1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 520 vod-peiyin-xuejie1
SET @voice_value := 'vod-peiyin-xuejie1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 520, '配音学姐', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-peiyin-xuejie1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-peiyin-xuejie1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 530 vod-jilupian-jieshuonan1
SET @voice_value := 'vod-jilupian-jieshuonan1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 530, '纪录片解说男', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-jilupian-jieshuonan1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-jilupian-jieshuonan1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 540 vod-kepu-jieshuonan1
SET @voice_value := 'vod-kepu-jieshuonan1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 540, '科普解说男', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-kepu-jieshuonan1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-kepu-jieshuonan1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 550 vod-lishi-jieshuonan1
SET @voice_value := 'vod-lishi-jieshuonan1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 550, '历史解说男', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-lishi-jieshuonan1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-lishi-jieshuonan1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 560 vod-peiyin-jieshuoxiaoshuai
SET @voice_value := 'vod-peiyin-jieshuoxiaoshuai';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 560, '解说小帅-精品版', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-peiyin-jieshuoxiaoshuai","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-peiyin-jieshuoxiaoshuai.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 570 vod-peiyin-jieshuonan1
SET @voice_value := 'vod-peiyin-jieshuonan1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 570, '配音解说男1', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-peiyin-jieshuonan1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-peiyin-jieshuonan1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 580 vod-peiyin-jieshuonan2
SET @voice_value := 'vod-peiyin-jieshuonan2';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 580, '配音解说男2', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-peiyin-jieshuonan2","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-peiyin-jieshuonan2.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 590 vod-duanju-jieshuonv1
SET @voice_value := 'vod-duanju-jieshuonv1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 590, '短剧解说女1', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-duanju-jieshuonv1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-duanju-jieshuonv1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 600 vod-tongyong-nan1
SET @voice_value := 'vod-tongyong-nan1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 600, '通用男1', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-tongyong-nan1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-tongyong-nan1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 610 vod-tongyong-nan2
SET @voice_value := 'vod-tongyong-nan2';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 610, '通用男2', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-tongyong-nan2","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-tongyong-nan2.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 620 vod-tongyong-nv1
SET @voice_value := 'vod-tongyong-nv1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 620, '通用女1', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-tongyong-nv1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-tongyong-nv1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 630 vod-tongyong-nv2
SET @voice_value := 'vod-tongyong-nv2';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 630, '通用女2', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-tongyong-nv2","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-tongyong-nv2.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 640 vod-clone-douyinduanjujieshuo1
SET @voice_value := 'vod-clone-douyinduanjujieshuo1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 640, '爆款解说1', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-clone-douyinduanjujieshuo1","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-clone-douyinduanjujieshuo1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 650 vod-clone-douyinduanjujieshuo2
SET @voice_value := 'vod-clone-douyinduanjujieshuo2';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 650, '爆款解说2', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-clone-douyinduanjujieshuo2","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-clone-douyinduanjujieshuo2.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 660 vod-clone-douyinduanjujieshuo4
SET @voice_value := 'vod-clone-douyinduanjujieshuo4';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 660, '爆款解说3', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"vod-clone-douyinduanjujieshuo4","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/vod-clone-douyinduanjujieshuo4.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 670 male-qn-qingse
SET @voice_value := 'male-qn-qingse';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 670, '青涩青年音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-qingse","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"青涩青年音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-qingse.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 680 male-qn-jingying
SET @voice_value := 'male-qn-jingying';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 680, '精英青年音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-jingying","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"精英青年音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-jingying.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 690 male-qn-badao
SET @voice_value := 'male-qn-badao';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 690, '霸道青年音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-badao","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"霸道青年音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-badao.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 700 male-qn-daxuesheng
SET @voice_value := 'male-qn-daxuesheng';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 700, '青年大学生音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"male-qn-daxuesheng","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"青年大学生音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/male-qn-daxuesheng.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 710 female-shaonv
SET @voice_value := 'female-shaonv';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 710, '少女音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-shaonv","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"少女音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-shaonv.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 720 female-yujie
SET @voice_value := 'female-yujie';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 720, '御姐音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-yujie","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"御姐音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-yujie.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 730 female-chengshu
SET @voice_value := 'female-chengshu';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 730, '成熟女性音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-chengshu","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"成熟女性音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-chengshu.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 740 female-tianmei
SET @voice_value := 'female-tianmei';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 740, '甜美女性音色', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"female-tianmei","model":"speech-2.8-turbo","language":"zh-CN","country":"中国","description":"甜美女性音色","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/female-tianmei.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 750 Cantonese_ProfessionalHost（F)
SET @voice_value := 'Cantonese_ProfessionalHost（F)';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 750, '专业女主持', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Cantonese_ProfessionalHost（F)","model":"speech-2.8-turbo","language":"zh-HK","country":"中国香港","description":"专业女主持（粤）","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Cantonese_ProfessionalHost%EF%BC%88F).mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 760 Cantonese_GentleLady
SET @voice_value := 'Cantonese_GentleLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 760, '温柔女声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Cantonese_GentleLady","model":"speech-2.8-turbo","language":"zh-HK","country":"中国香港","description":"温柔女声（粤）","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Cantonese_GentleLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 770 Cantonese_ProfessionalHost（M)
SET @voice_value := 'Cantonese_ProfessionalHost（M)';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 770, '专业男主持', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Cantonese_ProfessionalHost（M)","model":"speech-2.8-turbo","language":"zh-HK","country":"中国香港","description":"专业男主持（粤）","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Cantonese_ProfessionalHost%EF%BC%88M).mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 780 Cantonese_PlayfulMan
SET @voice_value := 'Cantonese_PlayfulMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 780, '活泼男声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Cantonese_PlayfulMan","model":"speech-2.8-turbo","language":"zh-HK","country":"中国香港","description":"活泼男声（粤）","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Cantonese_PlayfulMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 790 Cantonese_CuteGirl
SET @voice_value := 'Cantonese_CuteGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 790, '可爱女孩', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Cantonese_CuteGirl","model":"speech-2.8-turbo","language":"zh-HK","country":"中国香港","description":"可爱女孩（粤）","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Cantonese_CuteGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 800 Cantonese_KindWoman
SET @voice_value := 'Cantonese_KindWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 800, '善良女声', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Cantonese_KindWoman","model":"speech-2.8-turbo","language":"zh-HK","country":"中国香港","description":"善良女声（粤）","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Cantonese_KindWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 810 Santa_Claus
SET @voice_value := 'Santa_Claus';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 810, 'Santa Claus', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Santa_Claus","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Santa_Claus.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 820 Grinch
SET @voice_value := 'Grinch';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 820, 'Grinch', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Grinch","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Grinch","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Grinch.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 830 Rudolph
SET @voice_value := 'Rudolph';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 830, 'Rudolph', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Rudolph","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Rudolph","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Rudolph.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 840 Arnold
SET @voice_value := 'Arnold';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 840, 'Arnold', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Arnold","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Arnold","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Arnold.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 850 Charming_Santa
SET @voice_value := 'Charming_Santa';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 850, 'Charming Santa', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Charming_Santa","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Charming Santa","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Charming_Santa.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 860 Charming_Lady
SET @voice_value := 'Charming_Lady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 860, 'Charming Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Charming_Lady","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Charming Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Charming_Lady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 870 Sweet_Girl
SET @voice_value := 'Sweet_Girl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 870, 'Sweet Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Sweet_Girl","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Sweet Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Sweet_Girl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 880 Cute_Elf
SET @voice_value := 'Cute_Elf';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 880, 'Cute Elf', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Cute_Elf","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Cute Elf","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Cute_Elf.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 890 Attractive_Girl
SET @voice_value := 'Attractive_Girl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 890, 'Attractive Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Attractive_Girl","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Attractive Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Attractive_Girl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 900 Serene_Woman
SET @voice_value := 'Serene_Woman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 900, 'Serene Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Serene_Woman","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Serene Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Serene_Woman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 910 English_Trustworthy_Man
SET @voice_value := 'English_Trustworthy_Man';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 910, 'Trustworthy Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"English_Trustworthy_Man","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Trustworthy Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/English_Trustworthy_Man.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 920 English_Graceful_Lady
SET @voice_value := 'English_Graceful_Lady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 920, 'Graceful Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"English_Graceful_Lady","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Graceful Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/English_Graceful_Lady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 930 English_Aussie_Bloke
SET @voice_value := 'English_Aussie_Bloke';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 930, 'Aussie Bloke', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"English_Aussie_Bloke","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Aussie Bloke","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/English_Aussie_Bloke.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 940 English_Whispering_girl
SET @voice_value := 'English_Whispering_girl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 940, 'Whispering girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"English_Whispering_girl","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Whispering girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/English_Whispering_girl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 950 English_Diligent_Man
SET @voice_value := 'English_Diligent_Man';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 950, 'Diligent Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"English_Diligent_Man","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Diligent Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/English_Diligent_Man.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 960 English_Gentle-voiced_man
SET @voice_value := 'English_Gentle-voiced_man';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 960, 'Gentle-voiced man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"English_Gentle-voiced_man","model":"speech-2.8-turbo","language":"en-US","country":"美国","description":"Gentle-voiced man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/English_Gentle-voiced_man.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 970 Japanese_IntellectualSenior
SET @voice_value := 'Japanese_IntellectualSenior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 970, 'Intellectual Senior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_IntellectualSenior","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Intellectual Senior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_IntellectualSenior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 980 Japanese_DecisivePrincess
SET @voice_value := 'Japanese_DecisivePrincess';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 980, 'Decisive Princess', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_DecisivePrincess","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Decisive Princess","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_DecisivePrincess.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 990 Japanese_LoyalKnight
SET @voice_value := 'Japanese_LoyalKnight';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 990, 'Loyal Knight', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_LoyalKnight","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Loyal Knight","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_LoyalKnight.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1000 Japanese_DominantMan
SET @voice_value := 'Japanese_DominantMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1000, 'Dominant Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_DominantMan","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Dominant Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_DominantMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1010 Japanese_SeriousCommander
SET @voice_value := 'Japanese_SeriousCommander';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1010, 'Serious Commander', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_SeriousCommander","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Serious Commander","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_SeriousCommander.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1020 Japanese_ColdQueen
SET @voice_value := 'Japanese_ColdQueen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1020, 'Cold Queen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_ColdQueen","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Cold Queen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_ColdQueen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1030 Japanese_DependableWoman
SET @voice_value := 'Japanese_DependableWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1030, 'Dependable Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_DependableWoman","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Dependable Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_DependableWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1040 Japanese_GentleButler
SET @voice_value := 'Japanese_GentleButler';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1040, 'Gentle Butler', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_GentleButler","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Gentle Butler","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_GentleButler.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1050 Japanese_KindLady
SET @voice_value := 'Japanese_KindLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1050, 'Kind Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_KindLady","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Kind Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_KindLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1060 Japanese_CalmLady
SET @voice_value := 'Japanese_CalmLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1060, 'Calm Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_CalmLady","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Calm Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_CalmLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1070 Japanese_OptimisticYouth
SET @voice_value := 'Japanese_OptimisticYouth';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1070, 'Optimistic Youth', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_OptimisticYouth","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Optimistic Youth","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_OptimisticYouth.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1080 Japanese_GenerousIzakayaOwner
SET @voice_value := 'Japanese_GenerousIzakayaOwner';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1080, 'Generous Izakaya Owner', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_GenerousIzakayaOwner","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Generous Izakaya Owner","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_GenerousIzakayaOwner.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1090 Japanese_SportyStudent
SET @voice_value := 'Japanese_SportyStudent';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1090, 'Sporty Student', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_SportyStudent","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Sporty Student","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_SportyStudent.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1100 Japanese_InnocentBoy
SET @voice_value := 'Japanese_InnocentBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1100, 'Innocent Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_InnocentBoy","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Innocent Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_InnocentBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1110 Japanese_GracefulMaiden
SET @voice_value := 'Japanese_GracefulMaiden';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1110, 'Graceful Maiden', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Japanese_GracefulMaiden","model":"speech-2.8-turbo","language":"ja-JP","country":"日本","description":"Graceful Maiden","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Japanese_GracefulMaiden.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1120 Korean_SweetGirl
SET @voice_value := 'Korean_SweetGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1120, 'Sweet Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_SweetGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Sweet Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_SweetGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1130 Korean_CheerfulBoyfriend
SET @voice_value := 'Korean_CheerfulBoyfriend';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1130, 'Cheerful Boyfriend', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CheerfulBoyfriend","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Cheerful Boyfriend","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CheerfulBoyfriend.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1140 Korean_EnchantingSister
SET @voice_value := 'Korean_EnchantingSister';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1140, 'Enchanting Sister', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_EnchantingSister","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Enchanting Sister","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_EnchantingSister.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1150 Korean_ShyGirl
SET @voice_value := 'Korean_ShyGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1150, 'Shy Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ShyGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Shy Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ShyGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1160 Korean_ReliableSister
SET @voice_value := 'Korean_ReliableSister';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1160, 'Reliable Sister', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ReliableSister","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Reliable Sister","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ReliableSister.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1170 Korean_StrictBoss
SET @voice_value := 'Korean_StrictBoss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1170, 'Strict Boss', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_StrictBoss","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Strict Boss","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_StrictBoss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1180 Korean_SassyGirl
SET @voice_value := 'Korean_SassyGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1180, 'Sassy Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_SassyGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Sassy Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_SassyGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1190 Korean_ChildhoodFriendGirl
SET @voice_value := 'Korean_ChildhoodFriendGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1190, 'Childhood Friend Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ChildhoodFriendGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Childhood Friend Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ChildhoodFriendGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1200 Korean_PlayboyCharmer
SET @voice_value := 'Korean_PlayboyCharmer';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1200, 'Playboy Charmer', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_PlayboyCharmer","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Playboy Charmer","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_PlayboyCharmer.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1210 Korean_ElegantPrincess
SET @voice_value := 'Korean_ElegantPrincess';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1210, 'Elegant Princess', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ElegantPrincess","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Elegant Princess","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ElegantPrincess.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1220 Korean_BraveFemaleWarrior
SET @voice_value := 'Korean_BraveFemaleWarrior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1220, 'Brave Female Warrior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_BraveFemaleWarrior","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Brave Female Warrior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_BraveFemaleWarrior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1230 Korean_BraveYouth
SET @voice_value := 'Korean_BraveYouth';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1230, 'Brave Youth', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_BraveYouth","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Brave Youth","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_BraveYouth.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1240 Korean_CalmLady
SET @voice_value := 'Korean_CalmLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1240, 'Calm Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CalmLady","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Calm Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CalmLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1250 Korean_EnthusiasticTeen
SET @voice_value := 'Korean_EnthusiasticTeen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1250, 'Enthusiastic Teen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_EnthusiasticTeen","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Enthusiastic Teen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_EnthusiasticTeen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1260 Korean_SoothingLady
SET @voice_value := 'Korean_SoothingLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1260, 'Soothing Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_SoothingLady","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Soothing Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_SoothingLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1270 Korean_IntellectualSenior
SET @voice_value := 'Korean_IntellectualSenior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1270, 'Intellectual Senior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_IntellectualSenior","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Intellectual Senior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_IntellectualSenior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1280 Korean_LonelyWarrior
SET @voice_value := 'Korean_LonelyWarrior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1280, 'Lonely Warrior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_LonelyWarrior","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Lonely Warrior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_LonelyWarrior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1290 Korean_MatureLady
SET @voice_value := 'Korean_MatureLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1290, 'Mature Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_MatureLady","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Mature Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_MatureLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1300 Korean_InnocentBoy
SET @voice_value := 'Korean_InnocentBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1300, 'Innocent Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_InnocentBoy","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Innocent Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_InnocentBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1310 Korean_CharmingSister
SET @voice_value := 'Korean_CharmingSister';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1310, 'Charming Sister', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CharmingSister","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Charming Sister","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CharmingSister.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1320 Korean_AthleticStudent
SET @voice_value := 'Korean_AthleticStudent';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1320, 'Athletic Student', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_AthleticStudent","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Athletic Student","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_AthleticStudent.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1330 Korean_BraveAdventurer
SET @voice_value := 'Korean_BraveAdventurer';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1330, 'Brave Adventurer', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_BraveAdventurer","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Brave Adventurer","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_BraveAdventurer.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1340 Korean_CalmGentleman
SET @voice_value := 'Korean_CalmGentleman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1340, 'Calm Gentleman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CalmGentleman","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Calm Gentleman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CalmGentleman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1350 Korean_WiseElf
SET @voice_value := 'Korean_WiseElf';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1350, 'Wise Elf', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_WiseElf","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Wise Elf","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_WiseElf.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1360 Korean_CheerfulCoolJunior
SET @voice_value := 'Korean_CheerfulCoolJunior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1360, 'Cheerful Cool Junior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CheerfulCoolJunior","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Cheerful Cool Junior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CheerfulCoolJunior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1370 Korean_DecisiveQueen
SET @voice_value := 'Korean_DecisiveQueen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1370, 'Decisive Queen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_DecisiveQueen","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Decisive Queen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_DecisiveQueen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1380 Korean_ColdYoungMan
SET @voice_value := 'Korean_ColdYoungMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1380, 'Cold Young Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ColdYoungMan","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Cold Young Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ColdYoungMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1390 Korean_MysteriousGirl
SET @voice_value := 'Korean_MysteriousGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1390, 'Mysterious Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_MysteriousGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Mysterious Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_MysteriousGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1400 Korean_QuirkyGirl
SET @voice_value := 'Korean_QuirkyGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1400, 'Quirky Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_QuirkyGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Quirky Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_QuirkyGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1410 Korean_ConsiderateSenior
SET @voice_value := 'Korean_ConsiderateSenior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1410, 'Considerate Senior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ConsiderateSenior","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Considerate Senior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ConsiderateSenior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1420 Korean_CheerfulLittleSister
SET @voice_value := 'Korean_CheerfulLittleSister';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1420, 'Cheerful Little Sister', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CheerfulLittleSister","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Cheerful Little Sister","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CheerfulLittleSister.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1430 Korean_DominantMan
SET @voice_value := 'Korean_DominantMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1430, 'Dominant Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_DominantMan","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Dominant Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_DominantMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1440 Korean_AirheadedGirl
SET @voice_value := 'Korean_AirheadedGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1440, 'Airheaded Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_AirheadedGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Airheaded Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_AirheadedGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1450 Korean_ReliableYouth
SET @voice_value := 'Korean_ReliableYouth';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1450, 'Reliable Youth', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ReliableYouth","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Reliable Youth","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ReliableYouth.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1460 Korean_FriendlyBigSister
SET @voice_value := 'Korean_FriendlyBigSister';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1460, 'Friendly Big Sister', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_FriendlyBigSister","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Friendly Big Sister","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_FriendlyBigSister.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1470 Korean_GentleBoss
SET @voice_value := 'Korean_GentleBoss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1470, 'Gentle Boss', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_GentleBoss","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Gentle Boss","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_GentleBoss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1480 Korean_ColdGirl
SET @voice_value := 'Korean_ColdGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1480, 'Cold Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ColdGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Cold Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ColdGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1490 Korean_HaughtyLady
SET @voice_value := 'Korean_HaughtyLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1490, 'Haughty Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_HaughtyLady","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Haughty Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_HaughtyLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1500 Korean_CharmingElderSister
SET @voice_value := 'Korean_CharmingElderSister';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1500, 'Charming Elder Sister', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CharmingElderSister","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Charming Elder Sister","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CharmingElderSister.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1510 Korean_IntellectualMan
SET @voice_value := 'Korean_IntellectualMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1510, 'Intellectual Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_IntellectualMan","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Intellectual Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_IntellectualMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1520 Korean_CaringWoman
SET @voice_value := 'Korean_CaringWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1520, 'Caring Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CaringWoman","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Caring Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CaringWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1530 Korean_WiseTeacher
SET @voice_value := 'Korean_WiseTeacher';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1530, 'Wise Teacher', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_WiseTeacher","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Wise Teacher","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_WiseTeacher.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1540 Korean_ConfidentBoss
SET @voice_value := 'Korean_ConfidentBoss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1540, 'Confident Boss', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ConfidentBoss","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Confident Boss","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ConfidentBoss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1550 Korean_AthleticGirl
SET @voice_value := 'Korean_AthleticGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1550, 'Athletic Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_AthleticGirl","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Athletic Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_AthleticGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1560 Korean_PossessiveMan
SET @voice_value := 'Korean_PossessiveMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1560, 'Possessive Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_PossessiveMan","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Possessive Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_PossessiveMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1570 Korean_GentleWoman
SET @voice_value := 'Korean_GentleWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1570, 'Gentle Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_GentleWoman","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Gentle Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_GentleWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1580 Korean_CockyGuy
SET @voice_value := 'Korean_CockyGuy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1580, 'Cocky Guy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_CockyGuy","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Cocky Guy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_CockyGuy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1590 Korean_ThoughtfulWoman
SET @voice_value := 'Korean_ThoughtfulWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1590, 'Thoughtful Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_ThoughtfulWoman","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Thoughtful Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_ThoughtfulWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1600 Korean_OptimisticYouth
SET @voice_value := 'Korean_OptimisticYouth';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1600, 'Optimistic Youth', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Korean_OptimisticYouth","model":"speech-2.8-turbo","language":"ko-KR","country":"韩国","description":"Optimistic Youth","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Korean_OptimisticYouth.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1610 Spanish_SereneWoman
SET @voice_value := 'Spanish_SereneWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1610, 'Serene Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_SereneWoman","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Serene Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_SereneWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1620 Spanish_MaturePartner
SET @voice_value := 'Spanish_MaturePartner';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1620, 'Mature Partner', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_MaturePartner","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Mature Partner","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_MaturePartner.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1630 Spanish_CaptivatingStoryteller
SET @voice_value := 'Spanish_CaptivatingStoryteller';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1630, 'Captivating Storyteller', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_CaptivatingStoryteller","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Captivating Storyteller","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_CaptivatingStoryteller.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1640 Spanish_Narrator
SET @voice_value := 'Spanish_Narrator';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1640, 'Narrator', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Narrator","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Narrator","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Narrator.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1650 Spanish_WiseScholar
SET @voice_value := 'Spanish_WiseScholar';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1650, 'Wise Scholar', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_WiseScholar","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Wise Scholar","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_WiseScholar.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1660 Spanish_Kind-heartedGirl
SET @voice_value := 'Spanish_Kind-heartedGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1660, 'Kind-hearted Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Kind-heartedGirl","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Kind-hearted Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Kind-heartedGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1670 Spanish_DeterminedManager
SET @voice_value := 'Spanish_DeterminedManager';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1670, 'Determined Manager', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_DeterminedManager","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Determined Manager","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_DeterminedManager.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1680 Spanish_BossyLeader
SET @voice_value := 'Spanish_BossyLeader';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1680, 'Bossy Leader', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_BossyLeader","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Bossy Leader","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_BossyLeader.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1690 Spanish_ReservedYoungMan
SET @voice_value := 'Spanish_ReservedYoungMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1690, 'Reserved Young Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_ReservedYoungMan","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Reserved Young Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_ReservedYoungMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1700 Spanish_ConfidentWoman
SET @voice_value := 'Spanish_ConfidentWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1700, 'Confident Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_ConfidentWoman","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Confident Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_ConfidentWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1710 Spanish_ThoughtfulMan
SET @voice_value := 'Spanish_ThoughtfulMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1710, 'Thoughtful Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_ThoughtfulMan","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Thoughtful Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_ThoughtfulMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1720 Spanish_Strong-WilledBoy
SET @voice_value := 'Spanish_Strong-WilledBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1720, 'Strong-willed Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Strong-WilledBoy","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Strong-willed Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Strong-WilledBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1730 Spanish_SophisticatedLady
SET @voice_value := 'Spanish_SophisticatedLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1730, 'Sophisticated Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_SophisticatedLady","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Sophisticated Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_SophisticatedLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1740 Spanish_RationalMan
SET @voice_value := 'Spanish_RationalMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1740, 'Rational Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_RationalMan","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Rational Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_RationalMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1750 Spanish_AnimeCharacter
SET @voice_value := 'Spanish_AnimeCharacter';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1750, 'Anime Character', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_AnimeCharacter","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Anime Character","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_AnimeCharacter.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1760 Spanish_Deep-tonedMan
SET @voice_value := 'Spanish_Deep-tonedMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1760, 'Deep-toned Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Deep-tonedMan","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Deep-toned Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Deep-tonedMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1770 Spanish_Fussyhostess
SET @voice_value := 'Spanish_Fussyhostess';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1770, 'Fussy hostess', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Fussyhostess","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Fussy hostess","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Fussyhostess.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1780 Spanish_SincereTeen
SET @voice_value := 'Spanish_SincereTeen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1780, 'Sincere Teen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_SincereTeen","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Sincere Teen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_SincereTeen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1790 Spanish_FrankLady
SET @voice_value := 'Spanish_FrankLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1790, 'Frank Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_FrankLady","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Frank Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_FrankLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1800 Spanish_Comedian
SET @voice_value := 'Spanish_Comedian';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1800, 'Comedian', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Comedian","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Comedian","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Comedian.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1810 Spanish_Debator
SET @voice_value := 'Spanish_Debator';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1810, 'Debator', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Debator","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Debator","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Debator.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1820 Spanish_ToughBoss
SET @voice_value := 'Spanish_ToughBoss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1820, 'Tough Boss', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_ToughBoss","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Tough Boss","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_ToughBoss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1830 Spanish_Wiselady
SET @voice_value := 'Spanish_Wiselady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1830, 'Wise Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Wiselady","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Wise Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Wiselady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1840 Spanish_Steadymentor
SET @voice_value := 'Spanish_Steadymentor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1840, 'Steady Mentor', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Steadymentor","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Steady Mentor","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Steadymentor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1850 Spanish_Jovialman
SET @voice_value := 'Spanish_Jovialman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1850, 'Jovial Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Jovialman","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Jovial Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Jovialman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1860 Spanish_SantaClaus
SET @voice_value := 'Spanish_SantaClaus';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1860, 'Santa Claus', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_SantaClaus","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Santa Claus","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_SantaClaus.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1870 Spanish_Rudolph
SET @voice_value := 'Spanish_Rudolph';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1870, 'Rudolph', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Rudolph","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Rudolph","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Rudolph.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1880 Spanish_Intonategirl
SET @voice_value := 'Spanish_Intonategirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1880, 'Intonate Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Intonategirl","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Intonate Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Intonategirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1890 Spanish_Arnold
SET @voice_value := 'Spanish_Arnold';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1890, 'Arnold', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Arnold","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Arnold","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Arnold.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1900 Spanish_Ghost
SET @voice_value := 'Spanish_Ghost';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1900, 'Ghost', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_Ghost","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Ghost","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_Ghost.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1910 Spanish_HumorousElder
SET @voice_value := 'Spanish_HumorousElder';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1910, 'Humorous Elder', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_HumorousElder","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Humorous Elder","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_HumorousElder.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1920 Spanish_EnergeticBoy
SET @voice_value := 'Spanish_EnergeticBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1920, 'Energetic Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_EnergeticBoy","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Energetic Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_EnergeticBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1930 Spanish_WhimsicalGirl
SET @voice_value := 'Spanish_WhimsicalGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1930, 'Whimsical Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_WhimsicalGirl","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Whimsical Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_WhimsicalGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1940 Spanish_StrictBoss
SET @voice_value := 'Spanish_StrictBoss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1940, 'Strict Boss', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_StrictBoss","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Strict Boss","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_StrictBoss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1950 Spanish_ReliableMan
SET @voice_value := 'Spanish_ReliableMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1950, 'Reliable Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_ReliableMan","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Reliable Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_ReliableMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1960 Spanish_SereneElder
SET @voice_value := 'Spanish_SereneElder';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1960, 'Serene Elder', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_SereneElder","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Serene Elder","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_SereneElder.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1970 Spanish_AngryMan
SET @voice_value := 'Spanish_AngryMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1970, 'Angry Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_AngryMan","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Angry Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_AngryMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1980 Spanish_AssertiveQueen
SET @voice_value := 'Spanish_AssertiveQueen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1980, 'Assertive Queen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_AssertiveQueen","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Assertive Queen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_AssertiveQueen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 1990 Spanish_CaringGirlfriend
SET @voice_value := 'Spanish_CaringGirlfriend';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 1990, 'Caring Girlfriend', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_CaringGirlfriend","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Caring Girlfriend","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_CaringGirlfriend.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2000 Spanish_PowerfulSoldier
SET @voice_value := 'Spanish_PowerfulSoldier';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2000, 'Powerful Soldier', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_PowerfulSoldier","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Powerful Soldier","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_PowerfulSoldier.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2010 Spanish_PassionateWarrior
SET @voice_value := 'Spanish_PassionateWarrior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2010, 'Passionate Warrior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_PassionateWarrior","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Passionate Warrior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_PassionateWarrior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2020 Spanish_ChattyGirl
SET @voice_value := 'Spanish_ChattyGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2020, 'Chatty Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_ChattyGirl","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Chatty Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_ChattyGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2030 Spanish_RomanticHusband
SET @voice_value := 'Spanish_RomanticHusband';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2030, 'Romantic Husband', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_RomanticHusband","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Romantic Husband","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_RomanticHusband.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2040 Spanish_CompellingGirl
SET @voice_value := 'Spanish_CompellingGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2040, 'Compelling Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_CompellingGirl","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Compelling Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_CompellingGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2050 Spanish_PowerfulVeteran
SET @voice_value := 'Spanish_PowerfulVeteran';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2050, 'Powerful Veteran', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_PowerfulVeteran","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Powerful Veteran","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_PowerfulVeteran.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2060 Spanish_SensibleManager
SET @voice_value := 'Spanish_SensibleManager';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2060, 'Sensible Manager', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_SensibleManager","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Sensible Manager","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_SensibleManager.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2070 Spanish_ThoughtfulLady
SET @voice_value := 'Spanish_ThoughtfulLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2070, 'Thoughtful Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Spanish_ThoughtfulLady","model":"speech-2.8-turbo","language":"es-ES","country":"西班牙","description":"Thoughtful Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Spanish_ThoughtfulLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2080 Portuguese_SentimentalLady
SET @voice_value := 'Portuguese_SentimentalLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2080, 'Sentimental Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SentimentalLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Sentimental Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SentimentalLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2090 Portuguese_BossyLeader
SET @voice_value := 'Portuguese_BossyLeader';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2090, 'Bossy Leader', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_BossyLeader","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Bossy Leader","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_BossyLeader.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2100 Portuguese_Wiselady
SET @voice_value := 'Portuguese_Wiselady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2100, 'Wise lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Wiselady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Wise lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Wiselady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2110 Portuguese_Strong-WilledBoy
SET @voice_value := 'Portuguese_Strong-WilledBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2110, 'Strong-willed Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Strong-WilledBoy","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Strong-willed Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Strong-WilledBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2120 Portuguese_Deep-VoicedGentleman
SET @voice_value := 'Portuguese_Deep-VoicedGentleman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2120, 'Deep-voiced Gentleman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Deep-VoicedGentleman","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Deep-voiced Gentleman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Deep-VoicedGentleman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2130 Portuguese_UpsetGirl
SET @voice_value := 'Portuguese_UpsetGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2130, 'Upset Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_UpsetGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Upset Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_UpsetGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2140 Portuguese_PassionateWarrior
SET @voice_value := 'Portuguese_PassionateWarrior';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2140, 'Passionate Warrior', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_PassionateWarrior","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Passionate Warrior","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_PassionateWarrior.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2150 Portuguese_AnimeCharacter
SET @voice_value := 'Portuguese_AnimeCharacter';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2150, 'Anime Character', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_AnimeCharacter","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Anime Character","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_AnimeCharacter.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2160 Portuguese_ConfidentWoman
SET @voice_value := 'Portuguese_ConfidentWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2160, 'Confident Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ConfidentWoman","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Confident Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ConfidentWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2170 Portuguese_AngryMan
SET @voice_value := 'Portuguese_AngryMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2170, 'Angry Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_AngryMan","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Angry Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_AngryMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2180 Portuguese_CaptivatingStoryteller
SET @voice_value := 'Portuguese_CaptivatingStoryteller';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2180, 'Captivating Storyteller', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_CaptivatingStoryteller","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Captivating Storyteller","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_CaptivatingStoryteller.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2190 Portuguese_Godfather
SET @voice_value := 'Portuguese_Godfather';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2190, 'Godfather', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Godfather","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Godfather","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Godfather.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2200 Portuguese_ReservedYoungMan
SET @voice_value := 'Portuguese_ReservedYoungMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2200, 'Reserved Young Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ReservedYoungMan","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Reserved Young Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ReservedYoungMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2210 Portuguese_SmartYoungGirl
SET @voice_value := 'Portuguese_SmartYoungGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2210, 'Smart Young Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SmartYoungGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Smart Young Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SmartYoungGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2220 Portuguese_Kind-heartedGirl
SET @voice_value := 'Portuguese_Kind-heartedGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2220, 'Kind-hearted Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Kind-heartedGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Kind-hearted Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Kind-heartedGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2230 Portuguese_Pompouslady
SET @voice_value := 'Portuguese_Pompouslady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2230, 'Pompous lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Pompouslady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Pompous lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Pompouslady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2240 Portuguese_Grinch
SET @voice_value := 'Portuguese_Grinch';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2240, 'Grinch', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Grinch","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Grinch","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Grinch.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2250 Portuguese_Debator
SET @voice_value := 'Portuguese_Debator';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2250, 'Debator', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Debator","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Debator","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Debator.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2260 Portuguese_SweetGirl
SET @voice_value := 'Portuguese_SweetGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2260, 'Sweet Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SweetGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Sweet Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SweetGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2270 Portuguese_AttractiveGirl
SET @voice_value := 'Portuguese_AttractiveGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2270, 'Attractive Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_AttractiveGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Attractive Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_AttractiveGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2280 Portuguese_ThoughtfulMan
SET @voice_value := 'Portuguese_ThoughtfulMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2280, 'Thoughtful Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ThoughtfulMan","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Thoughtful Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ThoughtfulMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2290 Portuguese_PlayfulGirl
SET @voice_value := 'Portuguese_PlayfulGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2290, 'Playful Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_PlayfulGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Playful Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_PlayfulGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2300 Portuguese_GorgeousLady
SET @voice_value := 'Portuguese_GorgeousLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2300, 'Gorgeous Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_GorgeousLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Gorgeous Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_GorgeousLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2310 Portuguese_LovelyLady
SET @voice_value := 'Portuguese_LovelyLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2310, 'Lovely Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_LovelyLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Lovely Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_LovelyLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2320 Portuguese_SereneWoman
SET @voice_value := 'Portuguese_SereneWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2320, 'Serene Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SereneWoman","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Serene Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SereneWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2330 Portuguese_SadTeen
SET @voice_value := 'Portuguese_SadTeen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2330, 'Sad Teen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SadTeen","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Sad Teen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SadTeen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2340 Portuguese_MaturePartner
SET @voice_value := 'Portuguese_MaturePartner';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2340, 'Mature Partner', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_MaturePartner","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Mature Partner","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_MaturePartner.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2350 Portuguese_Comedian
SET @voice_value := 'Portuguese_Comedian';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2350, 'Comedian', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Comedian","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Comedian","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Comedian.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2360 Portuguese_NaughtySchoolgirl
SET @voice_value := 'Portuguese_NaughtySchoolgirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2360, 'Naughty Schoolgirl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_NaughtySchoolgirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Naughty Schoolgirl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_NaughtySchoolgirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2370 Portuguese_Narrator
SET @voice_value := 'Portuguese_Narrator';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2370, 'Narrator', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Narrator","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Narrator","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Narrator.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2380 Portuguese_ToughBoss
SET @voice_value := 'Portuguese_ToughBoss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2380, 'Tough Boss', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ToughBoss","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Tough Boss","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ToughBoss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2390 Portuguese_Fussyhostess
SET @voice_value := 'Portuguese_Fussyhostess';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2390, 'Fussy hostess', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Fussyhostess","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Fussy hostess","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Fussyhostess.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2400 Portuguese_Dramatist
SET @voice_value := 'Portuguese_Dramatist';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2400, 'Dramatist', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Dramatist","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Dramatist","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Dramatist.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2410 Portuguese_Steadymentor
SET @voice_value := 'Portuguese_Steadymentor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2410, 'Steady Mentor', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Steadymentor","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Steady Mentor","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Steadymentor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2420 Portuguese_Jovialman
SET @voice_value := 'Portuguese_Jovialman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2420, 'Jovial Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Jovialman","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Jovial Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Jovialman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2430 Portuguese_CharmingQueen
SET @voice_value := 'Portuguese_CharmingQueen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2430, 'Charming Queen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_CharmingQueen","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Charming Queen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_CharmingQueen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2440 Portuguese_SantaClaus
SET @voice_value := 'Portuguese_SantaClaus';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2440, 'Santa Claus', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SantaClaus","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Santa Claus","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SantaClaus.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2450 Portuguese_Rudolph
SET @voice_value := 'Portuguese_Rudolph';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2450, 'Rudolph', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Rudolph","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Rudolph","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Rudolph.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2460 Portuguese_Arnold
SET @voice_value := 'Portuguese_Arnold';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2460, 'Arnold', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Arnold","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Arnold","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Arnold.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2470 Portuguese_CharmingSanta
SET @voice_value := 'Portuguese_CharmingSanta';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2470, 'Charming Santa', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_CharmingSanta","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Charming Santa","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_CharmingSanta.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2480 Portuguese_CharmingLady
SET @voice_value := 'Portuguese_CharmingLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2480, 'Charming Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_CharmingLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Charming Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_CharmingLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2490 Portuguese_Ghost
SET @voice_value := 'Portuguese_Ghost';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2490, 'Ghost', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Ghost","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Ghost","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Ghost.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2500 Portuguese_HumorousElder
SET @voice_value := 'Portuguese_HumorousElder';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2500, 'Humorous Elder', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_HumorousElder","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Humorous Elder","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_HumorousElder.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2510 Portuguese_CalmLeader
SET @voice_value := 'Portuguese_CalmLeader';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2510, 'Calm Leader', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_CalmLeader","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Calm Leader","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_CalmLeader.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2520 Portuguese_GentleTeacher
SET @voice_value := 'Portuguese_GentleTeacher';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2520, 'Gentle Teacher', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_GentleTeacher","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Gentle Teacher","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_GentleTeacher.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2530 Portuguese_EnergeticBoy
SET @voice_value := 'Portuguese_EnergeticBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2530, 'Energetic Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_EnergeticBoy","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Energetic Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_EnergeticBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2540 Portuguese_ReliableMan
SET @voice_value := 'Portuguese_ReliableMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2540, 'Reliable Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ReliableMan","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Reliable Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ReliableMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2550 Portuguese_SereneElder
SET @voice_value := 'Portuguese_SereneElder';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2550, 'Serene Elder', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SereneElder","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Serene Elder","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SereneElder.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2560 Portuguese_GrimReaper
SET @voice_value := 'Portuguese_GrimReaper';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2560, 'Grim Reaper', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_GrimReaper","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Grim Reaper","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_GrimReaper.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2570 Portuguese_AssertiveQueen
SET @voice_value := 'Portuguese_AssertiveQueen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2570, 'Assertive Queen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_AssertiveQueen","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Assertive Queen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_AssertiveQueen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2580 Portuguese_WhimsicalGirl
SET @voice_value := 'Portuguese_WhimsicalGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2580, 'Whimsical Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_WhimsicalGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Whimsical Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_WhimsicalGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2590 Portuguese_StressedLady
SET @voice_value := 'Portuguese_StressedLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2590, 'Stressed Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_StressedLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Stressed Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_StressedLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2600 Portuguese_FriendlyNeighbor
SET @voice_value := 'Portuguese_FriendlyNeighbor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2600, 'Friendly Neighbor', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_FriendlyNeighbor","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Friendly Neighbor","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_FriendlyNeighbor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2610 Portuguese_CaringGirlfriend
SET @voice_value := 'Portuguese_CaringGirlfriend';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2610, 'Caring Girlfriend', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_CaringGirlfriend","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Caring Girlfriend","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_CaringGirlfriend.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2620 Portuguese_PowerfulSoldier
SET @voice_value := 'Portuguese_PowerfulSoldier';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2620, 'Powerful Soldier', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_PowerfulSoldier","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Powerful Soldier","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_PowerfulSoldier.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2630 Portuguese_FascinatingBoy
SET @voice_value := 'Portuguese_FascinatingBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2630, 'Fascinating Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_FascinatingBoy","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Fascinating Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_FascinatingBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2640 Portuguese_RomanticHusband
SET @voice_value := 'Portuguese_RomanticHusband';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2640, 'Romantic Husband', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_RomanticHusband","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Romantic Husband","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_RomanticHusband.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2650 Portuguese_StrictBoss
SET @voice_value := 'Portuguese_StrictBoss';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2650, 'Strict Boss', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_StrictBoss","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Strict Boss","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_StrictBoss.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2660 Portuguese_InspiringLady
SET @voice_value := 'Portuguese_InspiringLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2660, 'Inspiring Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_InspiringLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Inspiring Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_InspiringLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2670 Portuguese_PlayfulSpirit
SET @voice_value := 'Portuguese_PlayfulSpirit';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2670, 'Playful Spirit', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_PlayfulSpirit","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Playful Spirit","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_PlayfulSpirit.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2680 Portuguese_ElegantGirl
SET @voice_value := 'Portuguese_ElegantGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2680, 'Elegant Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ElegantGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Elegant Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ElegantGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2690 Portuguese_CompellingGirl
SET @voice_value := 'Portuguese_CompellingGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2690, 'Compelling Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_CompellingGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Compelling Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_CompellingGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2700 Portuguese_PowerfulVeteran
SET @voice_value := 'Portuguese_PowerfulVeteran';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2700, 'Powerful Veteran', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_PowerfulVeteran","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Powerful Veteran","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_PowerfulVeteran.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2710 Portuguese_SensibleManager
SET @voice_value := 'Portuguese_SensibleManager';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2710, 'Sensible Manager', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_SensibleManager","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Sensible Manager","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_SensibleManager.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2720 Portuguese_ThoughtfulLady
SET @voice_value := 'Portuguese_ThoughtfulLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2720, 'Thoughtful Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ThoughtfulLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Thoughtful Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ThoughtfulLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2730 Portuguese_TheatricalActor
SET @voice_value := 'Portuguese_TheatricalActor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2730, 'Theatrical Actor', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_TheatricalActor","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Theatrical Actor","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_TheatricalActor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2740 Portuguese_FragileBoy
SET @voice_value := 'Portuguese_FragileBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2740, 'Fragile Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_FragileBoy","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Fragile Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_FragileBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2750 Portuguese_ChattyGirl
SET @voice_value := 'Portuguese_ChattyGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2750, 'Chatty Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_ChattyGirl","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Chatty Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_ChattyGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2760 Portuguese_Conscientiousinstructor
SET @voice_value := 'Portuguese_Conscientiousinstructor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2760, 'Conscientious Instructor', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_Conscientiousinstructor","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Conscientious Instructor","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_Conscientiousinstructor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2770 Portuguese_RationalMan
SET @voice_value := 'Portuguese_RationalMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2770, 'Rational Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_RationalMan","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Rational Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_RationalMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2780 Portuguese_WiseScholar
SET @voice_value := 'Portuguese_WiseScholar';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2780, 'Wise Scholar', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_WiseScholar","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Wise Scholar","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_WiseScholar.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2790 Portuguese_FrankLady
SET @voice_value := 'Portuguese_FrankLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2790, 'Frank Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_FrankLady","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Frank Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_FrankLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2800 Portuguese_DeterminedManager
SET @voice_value := 'Portuguese_DeterminedManager';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2800, 'Determined Manager', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Portuguese_DeterminedManager","model":"speech-2.8-turbo","language":"pt-PT","country":"葡萄牙","description":"Determined Manager","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Portuguese_DeterminedManager.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2810 French_Male_Speech_New
SET @voice_value := 'French_Male_Speech_New';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2810, 'Level-Headed Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"French_Male_Speech_New","model":"speech-2.8-turbo","language":"fr-FR","country":"法国","description":"Level-Headed Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/French_Male_Speech_New.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2820 French_Female_News Anchor
SET @voice_value := 'French_Female_News Anchor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2820, 'Patient Female Presenter', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"French_Female_News Anchor","model":"speech-2.8-turbo","language":"fr-FR","country":"法国","description":"Patient Female Presenter","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/French_Female_News%20Anchor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2830 French_CasualMan
SET @voice_value := 'French_CasualMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2830, 'Casual Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"French_CasualMan","model":"speech-2.8-turbo","language":"fr-FR","country":"法国","description":"Casual Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/French_CasualMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2840 French_MovieLeadFemale
SET @voice_value := 'French_MovieLeadFemale';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2840, 'Movie Lead Female', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"French_MovieLeadFemale","model":"speech-2.8-turbo","language":"fr-FR","country":"法国","description":"Movie Lead Female","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/French_MovieLeadFemale.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2850 French_FemaleAnchor
SET @voice_value := 'French_FemaleAnchor';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2850, 'Female Anchor', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"French_FemaleAnchor","model":"speech-2.8-turbo","language":"fr-FR","country":"法国","description":"Female Anchor","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/French_FemaleAnchor.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2860 French_MaleNarrator
SET @voice_value := 'French_MaleNarrator';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2860, 'Male Narrator', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"French_MaleNarrator","model":"speech-2.8-turbo","language":"fr-FR","country":"法国","description":"Male Narrator","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/French_MaleNarrator.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2870 German_FriendlyMan
SET @voice_value := 'German_FriendlyMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2870, 'Friendly Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"German_FriendlyMan","model":"speech-2.8-turbo","language":"de-DE","country":"德国","description":"Friendly Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/German_FriendlyMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2880 German_SweetLady
SET @voice_value := 'German_SweetLady';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2880, 'Sweet Lady', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"German_SweetLady","model":"speech-2.8-turbo","language":"de-DE","country":"德国","description":"Sweet Lady","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/German_SweetLady.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2890 German_PlayfulMan
SET @voice_value := 'German_PlayfulMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2890, 'Playful Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"German_PlayfulMan","model":"speech-2.8-turbo","language":"de-DE","country":"德国","description":"Playful Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/German_PlayfulMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2900 Russian_HandsomeChildhoodFriend
SET @voice_value := 'Russian_HandsomeChildhoodFriend';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2900, 'Handsome Childhood Friend', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_HandsomeChildhoodFriend","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Handsome Childhood Friend","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_HandsomeChildhoodFriend.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2910 Russian_BrightHeroine
SET @voice_value := 'Russian_BrightHeroine';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2910, 'Bright Queen', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_BrightHeroine","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Bright Queen","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_BrightHeroine.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2920 Russian_AmbitiousWoman
SET @voice_value := 'Russian_AmbitiousWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2920, 'Ambitious Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_AmbitiousWoman","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Ambitious Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_AmbitiousWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2930 Russian_ReliableMan
SET @voice_value := 'Russian_ReliableMan';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2930, 'Reliable Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_ReliableMan","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Reliable Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_ReliableMan.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2940 Russian_CrazyQueen
SET @voice_value := 'Russian_CrazyQueen';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2940, 'Crazy Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_CrazyQueen","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Crazy Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_CrazyQueen.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2950 Russian_PessimisticGirl
SET @voice_value := 'Russian_PessimisticGirl';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2950, 'Pessimistic Girl', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_PessimisticGirl","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Pessimistic Girl","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_PessimisticGirl.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2960 Russian_AttractiveGuy
SET @voice_value := 'Russian_AttractiveGuy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2960, 'Attractive Guy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_AttractiveGuy","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Attractive Guy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_AttractiveGuy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2970 Russian_Bad-temperedBoy
SET @voice_value := 'Russian_Bad-temperedBoy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2970, 'Bad-tempered Boy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Russian_Bad-temperedBoy","model":"speech-2.8-turbo","language":"ru-RU","country":"俄罗斯","description":"Bad-tempered Boy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Russian_Bad-temperedBoy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2980 Arabic_CalmWoman
SET @voice_value := 'Arabic_CalmWoman';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2980, 'Calm Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Arabic_CalmWoman","model":"speech-2.8-turbo","language":"ar-SA","country":"沙特阿拉伯","description":"Calm Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Arabic_CalmWoman.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 2990 Arabic_FriendlyGuy
SET @voice_value := 'Arabic_FriendlyGuy';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 2990, 'Friendly Guy', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Arabic_FriendlyGuy","model":"speech-2.8-turbo","language":"ar-SA","country":"沙特阿拉伯","description":"Friendly Guy","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Arabic_FriendlyGuy.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 3000 Thai_male_1_sample8
SET @voice_value := 'Thai_male_1_sample8';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 3000, 'Serene Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Thai_male_1_sample8","model":"speech-2.8-turbo","language":"th-TH","country":"泰国","description":"Serene Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Thai_male_1_sample8.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 3010 Thai_male_2_sample2
SET @voice_value := 'Thai_male_2_sample2';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 3010, 'Friendly Man', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Thai_male_2_sample2","model":"speech-2.8-turbo","language":"th-TH","country":"泰国","description":"Friendly Man","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Thai_male_2_sample2.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 3020 Thai_female_1_sample1
SET @voice_value := 'Thai_female_1_sample1';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 3020, 'Confident Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Thai_female_1_sample1","model":"speech-2.8-turbo","language":"th-TH","country":"泰国","description":"Confident Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Thai_female_1_sample1.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

-- VOICE_RECORD 3030 Thai_female_2_sample2
SET @voice_value := 'Thai_female_2_sample2';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 3030, 'Energetic Woman', @voice_value, 'ai_tts_voice', 0, '', '', '{"voiceId":"Thai_female_2_sample2","model":"speech-2.8-turbo","language":"th-TH","country":"泰国","description":"Energetic Woman","previewUrl":"https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/Thai_female_2_sample2.mp3","isDefault":false}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_voice' AND CAST(`value` AS BINARY) = CAST(@voice_value AS BINARY));

SET @config_value := 'model';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 10, '模型', @config_value, 'ai_tts_config', 0, '', '', '{"value":"speech-2.8-turbo"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'output_format';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 20, '输出方式', @config_value, 'ai_tts_config', 0, '', '', '{"value":"url"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'subtitle_enable';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 30, '生成字幕', @config_value, 'ai_tts_config', 0, '', '', '{"value":"false"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'aigc_watermark';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 40, 'AIGC水印', @config_value, 'ai_tts_config', 0, '', '', '{"value":"false"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'language_boost';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 50, '语言增强', @config_value, 'ai_tts_config', 0, '', '', '{"value":"auto"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'speed';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 60, '语速', @config_value, 'ai_tts_config', 0, '', '', '{"value":"1.2"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'vol';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 70, '音量', @config_value, 'ai_tts_config', 0, '', '', '{"value":"1.1"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'pitch';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 80, '音调', @config_value, 'ai_tts_config', 0, '', '', '{"value":"0"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'emotion';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 90, '情绪', @config_value, 'ai_tts_config', 0, '', '', '{"value":"fluent"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'audio_format';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 100, '音频格式', @config_value, 'ai_tts_config', 0, '', '', '{"value":"mp3"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'sample_rate';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 110, '采样率', @config_value, 'ai_tts_config', 0, '', '', '{"value":"32000"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'bitrate';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 120, '比特率', @config_value, 'ai_tts_config', 0, '', '', '{"value":"128000"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

SET @config_value := 'channel';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `create_time`, `updater`, `update_time`)
SELECT 130, '声道数', @config_value, 'ai_tts_config', 0, '', '', '{"value":"1"}', '0', NOW(), '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` WHERE `dict_type` = 'ai_tts_config' AND CAST(`value` AS BINARY) = CAST(@config_value AS BINARY));

