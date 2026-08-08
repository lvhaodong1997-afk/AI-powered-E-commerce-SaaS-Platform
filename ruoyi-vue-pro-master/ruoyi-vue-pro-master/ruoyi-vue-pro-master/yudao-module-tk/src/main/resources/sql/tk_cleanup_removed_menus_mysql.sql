SET NAMES utf8mb4;

-- Hide business modules outside the retained System Management and TK Material Factory scope.
-- Run this after importing the upstream RuoYi-Vue-Pro MySQL seed data.
DROP TEMPORARY TABLE IF EXISTS tmp_tk_removed_menu_ids;
CREATE TEMPORARY TABLE tmp_tk_removed_menu_ids (
  id BIGINT PRIMARY KEY
);

INSERT INTO tmp_tk_removed_menu_ids (id)
WITH RECURSIVE menu_tree AS (
  SELECT id
  FROM system_menu
  WHERE id IN (
    1254, -- author news
    2159, -- boot docs
    2160, -- cloud docs
    2,    -- infra
    1117, -- pay
    1281, -- report
    1185, -- bpm
    2262, -- member
    2362, -- mall
    2084, -- mp
    2397, -- crm
    2563, -- erp
    6400, -- wms
    5100, -- mes
    2758, -- ai
    4000, -- iot
    6500  -- im
  )
  UNION ALL
  SELECT m.id
  FROM system_menu m
  INNER JOIN menu_tree t ON m.parent_id = t.id
  WHERE m.deleted = b'0'
)
SELECT DISTINCT id
FROM menu_tree;

UPDATE system_role_menu rm
INNER JOIN tmp_tk_removed_menu_ids t ON rm.menu_id = t.id
SET rm.deleted = b'1',
    rm.updater = 'tk-cleanup',
    rm.update_time = NOW();

UPDATE system_menu m
INNER JOIN tmp_tk_removed_menu_ids t ON m.id = t.id
SET m.deleted = b'1',
    m.updater = 'tk-cleanup',
    m.update_time = NOW();

DROP TEMPORARY TABLE IF EXISTS tmp_tk_removed_menu_ids;
