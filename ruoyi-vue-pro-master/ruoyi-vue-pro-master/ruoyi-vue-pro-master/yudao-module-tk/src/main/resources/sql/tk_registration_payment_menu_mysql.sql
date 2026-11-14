-- MySQL 8; limited to business packages containing TK root 6000 and tenant-admin roles.
-- Back up the three tables first. Rerunnable; collisions fail instead of overwriting other menus.
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS tk_install_registration_payment_menus;
DELIMITER $$
CREATE PROCEDURE tk_install_registration_payment_menus()
BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
  IF EXISTS (SELECT 1 FROM system_menu WHERE
      (id=6090 AND NOT(permission <=> 'tk:payment:query')) OR
      (id=6091 AND NOT(permission <=> 'tk:payment:create')) OR
      (id=6092 AND NOT(permission <=> 'system:tenant-invite:query'))) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TK billing menu ID collision (6090-6092)';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM system_menu WHERE id=6000 AND deleted=b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='TK menu root 6000 not found';
  END IF;
  START TRANSACTION;
  INSERT INTO system_menu
    (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
  VALUES
    (6090,'积分充值','tk:payment:query',2,90,6000,'recharge','ep:coin','tk/recharge/index','TkRecharge',0,b'1',b'0',b'1','admin',NOW(),'admin',NOW(),b'0'),
    (6091,'创建支付订单','tk:payment:create',3,1,6090,'','','',NULL,0,b'0',b'0',b'0','admin',NOW(),'admin',NOW(),b'0'),
    (6092,'邀请推荐','system:tenant-invite:query',2,91,6000,'invite','ep:share','tk/invite/index','TkInvite',0,b'1',b'0',b'1','admin',NOW(),'admin',NOW(),b'0')
  ON DUPLICATE KEY UPDATE status=0,deleted=b'0';
  UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',6090)
    WHERE deleted=b'0' AND status=0 AND JSON_CONTAINS(menu_ids,'6000') AND NOT JSON_CONTAINS(menu_ids,'6090');
  UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',6091)
    WHERE deleted=b'0' AND status=0 AND JSON_CONTAINS(menu_ids,'6000') AND NOT JSON_CONTAINS(menu_ids,'6091');
  UPDATE system_tenant_package SET menu_ids=JSON_ARRAY_APPEND(menu_ids,'$',6092)
    WHERE deleted=b'0' AND status=0 AND JSON_CONTAINS(menu_ids,'6000') AND NOT JSON_CONTAINS(menu_ids,'6092');
  INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
  SELECT r.id,m.id,'admin',NOW(),'admin',NOW(),b'0',r.tenant_id
  FROM system_role r
  JOIN system_tenant t ON t.id=r.tenant_id AND t.deleted=b'0'
  JOIN system_tenant_package p ON p.id=t.package_id AND p.deleted=b'0' AND p.status=0
  JOIN (SELECT 6090 id UNION ALL SELECT 6091 UNION ALL SELECT 6092) m
  WHERE r.code='tenant_admin' AND r.status=0 AND r.deleted=b'0' AND r.tenant_id>0
    AND JSON_CONTAINS(p.menu_ids,CAST(m.id AS JSON))
    AND NOT EXISTS (SELECT 1 FROM system_role_menu e WHERE e.role_id=r.id AND e.menu_id=m.id AND e.deleted=b'0');
  COMMIT;
END$$
DELIMITER ;
CALL tk_install_registration_payment_menus();
DROP PROCEDURE tk_install_registration_payment_menus;
