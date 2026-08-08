SET NAMES utf8mb4;

START TRANSACTION;

UPDATE `system_tenant`
SET `name` = CASE `id`
  WHEN 1 THEN 'TK自动混剪'
  WHEN 121 THEN '演示租户一'
  WHEN 122 THEN '演示租户二'
  ELSE `name`
END,
`contact_name` = CASE `id`
  WHEN 1 THEN '管理员'
  WHEN 121 THEN '联系人一'
  WHEN 122 THEN '联系人二'
  ELSE `contact_name`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0' AND `id` IN (1, 121, 122);

UPDATE `system_tenant_package`
SET `name` = CASE `id`
  WHEN 111 THEN '默认套餐'
  ELSE `name`
END,
`remark` = CASE `id`
  WHEN 111 THEN '默认套餐'
  ELSE `remark`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0' AND `id` IN (111);

UPDATE `system_users`
SET `nickname` = CASE `id`
  WHEN 1 THEN '管理员'
  WHEN 100 THEN '芋道'
  WHEN 103 THEN '源码'
  WHEN 104 THEN '测试号'
  WHEN 107 THEN '用户107'
  WHEN 108 THEN '用户108'
  WHEN 109 THEN '用户109'
  WHEN 110 THEN '用户110'
  WHEN 111 THEN '测试用户'
  WHEN 112 THEN '新用户'
  WHEN 113 THEN '奥特曼1'
  WHEN 114 THEN 'HR 管理员'
  WHEN 115 THEN '奥特曼'
  WHEN 117 THEN '测试用户02'
  WHEN 118 THEN '测试账号'
  WHEN 139 THEN '运营员'
  WHEN 141 THEN '管理员1'
  ELSE `nickname`
END,
`remark` = CASE `id`
  WHEN 1 THEN '管理员'
  WHEN 100 THEN '不要乱改'
  ELSE `remark`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0'
  AND `id` IN (1, 100, 103, 104, 107, 108, 109, 110, 111, 112, 113, 114, 115, 117, 118, 139, 141);

UPDATE `system_role`
SET `name` = CASE `id`
  WHEN 1 THEN '超级管理员'
  WHEN 2 THEN '普通角色'
  WHEN 3 THEN 'CRM 管理员'
  WHEN 109 THEN '租户管理员'
  WHEN 111 THEN '租户管理员'
  ELSE `name`
END,
`remark` = CASE `id`
  WHEN 1 THEN '超级管理员'
  WHEN 2 THEN '普通角色'
  WHEN 3 THEN 'CRM 管理员'
  WHEN 109 THEN '租户管理员'
  WHEN 111 THEN '租户管理员'
  ELSE `remark`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0' AND `id` IN (1, 2, 3, 109, 111);

UPDATE `system_dept`
SET `name` = CASE `id`
  WHEN 100 THEN '总公司'
  WHEN 101 THEN '深圳总部'
  WHEN 102 THEN '长沙分部'
  WHEN 103 THEN '研发部门'
  WHEN 104 THEN '市场部门'
  WHEN 105 THEN '测试部门'
  WHEN 106 THEN '财务部门'
  WHEN 107 THEN '运维部门'
  WHEN 108 THEN '市场部门'
  WHEN 109 THEN '财务部门'
  WHEN 110 THEN '部门一'
  WHEN 111 THEN '部门二'
  WHEN 112 THEN '研发部门'
  WHEN 113 THEN '研发部门'
  WHEN 116 THEN '演示部门'
  WHEN 117 THEN '演示部门 2'
  ELSE `name`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0'
  AND `id` IN (100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 116, 117);

UPDATE `system_post`
SET `name` = CASE `id`
  WHEN 2 THEN '项目经理'
  WHEN 4 THEN '普通员工'
  WHEN 5 THEN '人力资源'
  WHEN 7 THEN '测试'
  ELSE `name`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0' AND `id` IN (2, 4, 5, 7);

UPDATE `system_menu`
SET `name` = CASE `id`
  WHEN 1 THEN '系统管理'
  WHEN 100 THEN '用户管理'
  WHEN 101 THEN '角色管理'
  WHEN 102 THEN '菜单管理'
  WHEN 103 THEN '部门管理'
  WHEN 108 THEN '日志管理'
  WHEN 500 THEN '操作日志'
  WHEN 501 THEN '登录日志'
  WHEN 1001 THEN '用户查询'
  WHEN 1002 THEN '用户新增'
  WHEN 1003 THEN '用户修改'
  WHEN 1004 THEN '用户删除'
  WHEN 1005 THEN '用户导出'
  WHEN 1006 THEN '用户导入'
  WHEN 1007 THEN '重置密码'
  WHEN 1008 THEN '角色查询'
  WHEN 1009 THEN '角色新增'
  WHEN 1010 THEN '角色修改'
  WHEN 1011 THEN '角色删除'
  WHEN 1012 THEN '角色导出'
  WHEN 1013 THEN '菜单查询'
  WHEN 1014 THEN '菜单新增'
  WHEN 1015 THEN '菜单修改'
  WHEN 1016 THEN '菜单删除'
  WHEN 1017 THEN '部门查询'
  WHEN 1018 THEN '部门新增'
  WHEN 1019 THEN '部门修改'
  WHEN 1020 THEN '部门删除'
  WHEN 1040 THEN '操作日志查询'
  WHEN 1042 THEN '操作日志导出'
  WHEN 1043 THEN '登录日志查询'
  WHEN 1045 THEN '登录日志导出'
  WHEN 1063 THEN '分配角色菜单'
  WHEN 1064 THEN '分配数据权限'
  WHEN 1065 THEN '分配用户角色'
  WHEN 1138 THEN '租户列表'
  WHEN 1139 THEN '租户查询'
  WHEN 1140 THEN '租户新增'
  WHEN 1141 THEN '租户修改'
  WHEN 1142 THEN '租户删除'
  WHEN 1143 THEN '租户导出'
  WHEN 1224 THEN '租户管理'
  WHEN 1225 THEN '租户套餐'
  WHEN 1226 THEN '租户套餐查询'
  WHEN 1227 THEN '租户套餐新增'
  WHEN 1228 THEN '租户套餐修改'
  WHEN 1229 THEN '租户套餐删除'
  WHEN 5010 THEN '切换租户'
  WHEN 6010 THEN '公司查询'
  WHEN 6018 THEN '对标分析'
  ELSE `name`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0'
  AND `id` IN (
    1, 100, 101, 102, 103, 108, 500, 501,
    1001, 1002, 1003, 1004, 1005, 1006, 1007,
    1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020,
    1040, 1042, 1043, 1045, 1063, 1064, 1065,
    1138, 1139, 1140, 1141, 1142, 1143,
    1224, 1225, 1226, 1227, 1228, 1229, 5010, 6010, 6018
  );

UPDATE `system_dict_data`
SET `label` = CASE `dict_type`
  WHEN 'common_status' THEN CASE `value`
    WHEN '0' THEN '开启'
    WHEN '1' THEN '关闭'
    ELSE `label`
  END
  WHEN 'system_menu_type' THEN CASE `value`
    WHEN '1' THEN '目录'
    WHEN '2' THEN '菜单'
    WHEN '3' THEN '按钮'
    ELSE `label`
  END
  WHEN 'system_login_type' THEN CASE `value`
    WHEN '100' THEN '账号登录'
    WHEN '101' THEN '短信登录'
    WHEN '103' THEN '社交登录'
    WHEN '200' THEN '主动登出'
    WHEN '202' THEN '强制退出'
    ELSE `label`
  END
  WHEN 'system_login_result' THEN CASE `value`
    WHEN '0' THEN '成功'
    WHEN '10' THEN '账号或密码不正确'
    WHEN '20' THEN '用户被禁用'
    WHEN '30' THEN '验证码不存在'
    WHEN '31' THEN '验证码不正确'
    WHEN '100' THEN '未知错误'
    ELSE `label`
  END
  WHEN 'system_user_sex' THEN CASE `value`
    WHEN '1' THEN '男'
    WHEN '2' THEN '女'
    ELSE `label`
  END
  WHEN 'system_data_scope' THEN CASE `value`
    WHEN '1' THEN '全部数据权限'
    WHEN '2' THEN '指定部门数据权限'
    WHEN '3' THEN '部门数据权限'
    WHEN '4' THEN '部门及以下数据权限'
    WHEN '5' THEN '仅本人数据权限'
    ELSE `label`
  END
  ELSE `label`
END,
`updater` = 'codex-i18n-repair',
`update_time` = NOW()
WHERE `deleted` = b'0'
  AND `dict_type` IN (
    'common_status',
    'system_menu_type',
    'system_login_type',
    'system_login_result',
    'system_user_sex',
    'system_data_scope'
  );

COMMIT;
