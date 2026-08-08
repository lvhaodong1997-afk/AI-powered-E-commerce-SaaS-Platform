ALTER TABLE `tk_material_video`
  ADD COLUMN `usage_phase` varchar(32) NOT NULL DEFAULT 'GENERAL'
  COMMENT '素材用途：ATTENTION/PRODUCT_SHOW/RESULT_EFFECT/GENERAL'
  AFTER `tags`;

ALTER TABLE `tk_material_video`
  ADD COLUMN `segment_type` varchar(32) NOT NULL DEFAULT 'GENERAL'
  COMMENT '素材分段：S1_HOOK/S2_PAIN/S3_REVEAL/S4_DEMO/S5_PROOF/S6_DETAIL/S7_LIFESTYLE/GENERAL'
  AFTER `usage_phase`;

ALTER TABLE `tk_generation_task`
  ADD COLUMN `segment_timeline` text DEFAULT NULL
  COMMENT 'AI 输出分段调度时间轴'
  AFTER `script_text`;

ALTER TABLE `tk_reference_script_option`
  ADD COLUMN `segment_timeline` text DEFAULT NULL
  COMMENT '分段调度时间轴'
  AFTER `script_text`;
