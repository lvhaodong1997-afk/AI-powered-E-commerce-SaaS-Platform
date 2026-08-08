SET NAMES utf8mb4;

ALTER TABLE `tk_generation_task`
  MODIFY COLUMN `prompt_text` text DEFAULT NULL COMMENT 'AI 提示词或手动引流文案';
