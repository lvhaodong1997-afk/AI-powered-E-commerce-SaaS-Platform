package cn.iocoder.yudao.module.system.service.user;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthRegisterReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileUpdatePasswordReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserImportExcelVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserImportRespVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserPageReqVO;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.UserPostDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.UserPostMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleCodeEnum;
import cn.iocoder.yudao.module.system.enums.permission.RoleTypeEnum;
import cn.iocoder.yudao.module.system.mq.producer.user.AdminUserProducer;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.tenant.TkTenantAccessService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import com.google.common.annotations.VisibleForTesting;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.impl.DiffParseFunction;
import com.mzt.logapi.starter.annotation.LogRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.system.enums.LogRecordConstants.*;

/**
 * 后台用户 Service 实现类
 *
 * @author 秀美源码
 */
@Service("adminUserService")
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    static final String USER_INIT_PASSWORD_KEY = "system.user.init-password";

    static final String USER_REGISTER_ENABLED_KEY = "system.user.register-enabled";

    private static final String TK_USER_LEVEL_DEFAULT = "TENANT_USER";
    private static final String TK_TENANT_ADMIN = "TENANT_ADMIN";
    private static final String TK_TENANT_USER = "TENANT_USER";
    private static final String TK_COMPANY_ADMIN = "COMPANY_ADMIN";
    private static final String TK_COMPANY_USER = "COMPANY_USER";
    private static final String TK_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String INFO_KEY_TK_USER_LEVEL = "tkUserLevel";

    @Resource
    private AdminUserMapper userMapper;

    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    @Lazy // 延迟，避免循环依赖报错
    private TenantService tenantService;
    @Resource
    @Lazy // 懒加载，避免循环依赖
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private TkTenantAccessService tkTenantAccessService;

    @Resource
    private UserPostMapper userPostMapper;

    @Resource
    private ConfigApi configApi;

    @Resource
    private AdminUserProducer adminUserProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_CREATE_SUB_TYPE, bizNo = "{{#user.id}}",
            success = SYSTEM_USER_CREATE_SUCCESS)
    public Long createUser(UserSaveReqVO createReqVO) {
        if (TK_TENANT_ADMIN.equals(normalizeTkUserLevel(createReqVO.getTkUserLevel()))) {
            throw exception(USER_TK_TENANT_ADMIN_FORBIDDEN);
        }
        return createUserInternal(createReqVO, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTenantAdminUser(UserSaveReqVO createReqVO) {
        return createUserInternal(createReqVO, true);
    }

    private Long createUserInternal(UserSaveReqVO createReqVO, boolean allowTenantAdmin) {
        return tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            // 1.1 校验账户配合
            tenantService.handleTenantInfo(tenant -> {
                long count = userMapper.selectCount();
                if (count >= tenant.getAccountCount()) {
                    throw exception(USER_COUNT_MAX, tenant.getAccountCount());
                }
            });
            // 1.2 校验正确性
            validateUserForCreateOrUpdate(null, createReqVO.getUsername(),
                    createReqVO.getMobile(), createReqVO.getEmail());
            // 2.1 插入用户
            AdminUserDO user = BeanUtils.toBean(createReqVO, AdminUserDO.class);
            removeDeptAndPost(user);
            prepareTkUserScopeForSave(user, null, allowTenantAdmin);
            tkTenantAccessService.applyCurrentTenantForNonPlatform(user);
            user.setStatus(CommonStatusEnum.ENABLE.getStatus()); // 默认开启
            user.setPassword(encodePassword(createReqVO.getPassword())); // 加密密码
            userMapper.insert(user);
            assignDefaultTenantUserRole(user);

            // 3. 记录操作日志上下文
            LogRecordContext.putVariable("user", user);
            return user.getId();
        });
    }

    private void assignDefaultTenantUserRole(AdminUserDO user) {
        if (user == null || user.getTenantId() == null || !TK_TENANT_USER.equals(user.getTkUserLevel())) {
            return;
        }
        TenantUtils.execute(user.getTenantId(), () -> {
            RoleDO role = getOrCreateTenantUserRole(user.getTenantId());
            permissionService.assignUserRole(user.getId(), Collections.singleton(role.getId()));
        });
    }

    private RoleDO getOrCreateTenantUserRole(Long tenantId) {
        RoleDO role = roleMapper.selectByCodeAndTenantId(RoleCodeEnum.TENANT_USER.getCode(), tenantId);
        if (role != null) {
            return role;
        }
        RoleDO createRole = new RoleDO();
        createRole.setName(RoleCodeEnum.TENANT_USER.getName());
        createRole.setCode(RoleCodeEnum.TENANT_USER.getCode());
        createRole.setSort(1);
        createRole.setStatus(CommonStatusEnum.ENABLE.getStatus());
        createRole.setType(RoleTypeEnum.SYSTEM.getType());
        createRole.setDataScope(DataScopeEnum.ALL.getScope());
        createRole.setTenantId(tenantId);
        roleMapper.insert(createRole);
        tenantService.handleTenantMenu(menuIds -> permissionService.assignRoleMenu(createRole.getId(), menuIds));
        return createRole;
    }

    @Override
    public Long registerUser(AuthRegisterReqVO registerReqVO) {
        // 1.1 校验是否开启注册
        if (ObjUtil.notEqual(configApi.getConfigValueByKey(USER_REGISTER_ENABLED_KEY), "true")) {
            throw exception(USER_REGISTER_DISABLED);
        }
        // 1.2 校验账户配合
        tenantService.handleTenantInfo(tenant -> {
            long count = userMapper.selectCount();
            if (count >= tenant.getAccountCount()) {
                throw exception(USER_COUNT_MAX, tenant.getAccountCount());
            }
        });
        // 1.3 校验正确性
        validateUserForCreateOrUpdate(null, registerReqVO.getUsername(), null, null);

        // 2. 插入用户
        AdminUserDO user = BeanUtils.toBean(registerReqVO, AdminUserDO.class);
        removeDeptAndPost(user);
        prepareTkUserScopeForSave(user, null, false);
        tkTenantAccessService.applyCurrentTenantForNonPlatform(user);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus()); // 默认开启
        user.setPassword(encodePassword(registerReqVO.getPassword())); // 加密密码
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_UPDATE_SUB_TYPE, bizNo = "{{#updateReqVO.id}}",
            success = SYSTEM_USER_UPDATE_SUCCESS)
    public void updateUser(UserSaveReqVO updateReqVO) {
        tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            updateReqVO.setPassword(null); // 特殊：此处不更新密码
            // 1. 校验正确性
            AdminUserDO oldUser = validateUserForCreateOrUpdate(updateReqVO.getId(), updateReqVO.getUsername(),
                    updateReqVO.getMobile(), updateReqVO.getEmail());
            tkTenantAccessService.validateUserTenant(oldUser);

            // 2.1 更新用户
            AdminUserDO updateObj = BeanUtils.toBean(updateReqVO, AdminUserDO.class);
            removeDeptAndPost(updateObj);
            prepareTkUserScopeForSave(updateObj, oldUser, false);
            tkTenantAccessService.applyCurrentTenantForNonPlatform(updateObj);
            userMapper.updateById(updateObj);
            // 2.2 TK 业务不使用岗位，清理旧的用户岗位关系
            userPostMapper.deleteByUserId(updateReqVO.getId());
            // 2.3 昵称 / 头像变化时，发送消息供下游订阅（如 IM 模块推 FRIEND_INFO_UPDATED）
            publishUserProfileUpdatedIfChanged(oldUser, updateReqVO.getNickname(), updateReqVO.getAvatar());

            // 3. 记录操作日志上下文
            LogRecordContext.putVariable(DiffParseFunction.OLD_OBJECT, BeanUtils.toBean(oldUser, UserSaveReqVO.class));
            LogRecordContext.putVariable("user", oldUser);
        });
    }

    @Override
    public void updateUserLogin(Long id, String loginIp) {
        userMapper.updateById(new AdminUserDO().setId(id).setLoginIp(loginIp).setLoginDate(LocalDateTime.now()));
    }

    @Override
    public void updateUserProfile(Long id, UserProfileUpdateReqVO reqVO) {
        // 1. 校验正确性
        AdminUserDO oldUser = validateUserExists(id);
        validateEmailUnique(id, reqVO.getEmail());
        validateMobileUnique(id, reqVO.getMobile());

        // 2. 执行更新
        userMapper.updateById(BeanUtils.toBean(reqVO, AdminUserDO.class).setId(id));

        // 3. 昵称 / 头像变化时，发送消息供下游订阅（如 IM 模块推 FRIEND_INFO_UPDATED）
        publishUserProfileUpdatedIfChanged(oldUser, reqVO.getNickname(), reqVO.getAvatar());
    }

    /**
     * 仅当 nickname 或 avatar 跟旧值不一致时，发送 AdminUserProfileUpdateMessage
     */
    private void publishUserProfileUpdatedIfChanged(AdminUserDO oldUser, String newNickname, String newAvatar) {
        boolean nicknameChanged = newNickname != null && !ObjUtil.equal(oldUser.getNickname(), newNickname);
        boolean avatarChanged = newAvatar != null && !ObjUtil.equal(oldUser.getAvatar(), newAvatar);
        if (!nicknameChanged && !avatarChanged) {
            return;
        }
        adminUserProducer.sendUserProfileUpdateMessage(oldUser.getId(),
                nicknameChanged ? newNickname : null,
                avatarChanged ? newAvatar : null);
    }

    @Override
    public void updateUserPassword(Long id, UserProfileUpdatePasswordReqVO reqVO) {
        // 校验旧密码密码
        validateOldPassword(id, reqVO.getOldPassword());
        // 执行更新
        AdminUserDO updateObj = new AdminUserDO().setId(id);
        updateObj.setPassword(encodePassword(reqVO.getNewPassword())); // 加密密码
        userMapper.updateById(updateObj);
    }

    @Override
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_UPDATE_PASSWORD_SUB_TYPE, bizNo = "{{#id}}",
            success = SYSTEM_USER_UPDATE_PASSWORD_SUCCESS)
    public void updateUserPassword(Long id, String password) {
        tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            // 1. 校验用户存在
            AdminUserDO user = validateUserExists(id);
            tkTenantAccessService.validateUserTenant(user);

            // 2. 更新密码
            AdminUserDO updateObj = new AdminUserDO();
            updateObj.setId(id);
            updateObj.setPassword(encodePassword(password)); // 加密密码
            userMapper.updateById(updateObj);

            // 3. 记录操作日志上下文
            LogRecordContext.putVariable("user", user);
            LogRecordContext.putVariable("newPassword", updateObj.getPassword());
        });
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            // 校验用户存在
            AdminUserDO user = validateUserExists(id);
            tkTenantAccessService.validateUserTenant(user);
            // 更新状态
            AdminUserDO updateObj = new AdminUserDO();
            updateObj.setId(id);
            updateObj.setStatus(status);
            userMapper.updateById(updateObj);

            // 如果是禁用用户，则删除其 Token 信息
            if (CommonStatusEnum.isDisable(status)) {
                oauth2TokenService.removeAccessToken(id, UserTypeEnum.ADMIN.getValue());
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SYSTEM_USER_TYPE, subType = SYSTEM_USER_DELETE_SUB_TYPE, bizNo = "{{#id}}",
            success = SYSTEM_USER_DELETE_SUCCESS)
    public void deleteUser(Long id) {
        tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            // 1. 校验用户存在
            AdminUserDO user = validateUserExists(id);
            tkTenantAccessService.validateUserTenant(user);

            // 2.1 删除用户
            userMapper.deleteById(id);
            // 2.2 删除用户关联数据
            permissionService.processUserDeleted(id);
            // 2.2 删除用户岗位
            userPostMapper.deleteByUserId(id);

            // 3. 记录操作日志上下文
            LogRecordContext.putVariable("user", user);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUserList(List<Long> ids) {
        tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            ids.forEach(id -> tkTenantAccessService.validateUserTenant(validateUserExists(id)));
            // 1. 批量删除用户
            userMapper.deleteByIds(ids);

            // 2. 批量删除用户关联数据
            ids.forEach(id -> {
                permissionService.processUserDeleted(id);
                userPostMapper.deleteByUserId(id);
            });
        });
    }

    @Override
    public AdminUserDO getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    public AdminUserDO getUserByMobile(String mobile) {
        return userMapper.selectByMobile(mobile);
    }

    @Override
    public PageResult<AdminUserDO> getUserPage(UserPageReqVO reqVO) {
        return tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            // 如果有角色编号，查询角色对应的用户编号
            Set<Long> userIds = null;
            if (reqVO.getRoleId() != null) {
                userIds = permissionService.getUserRoleIdListByRoleId(singleton(reqVO.getRoleId()));
                if (CollUtil.isEmpty(userIds)) {
                    return PageResult.empty();
                }
            }

            // 分页查询
            return userMapper.selectPage(reqVO, Collections.emptySet(), userIds, getRequestedTenantId(reqVO.getTenantId()));
        });
    }

    @Override
    public AdminUserDO getUser(Long id) {
        return tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> {
            AdminUserDO user = userMapper.selectById(id);
            tkTenantAccessService.validateUserTenant(user);
            return user;
        });
    }

    @Override
    public List<AdminUserDO> getUserListByDeptIds(Collection<Long> deptIds) {
        if (CollUtil.isEmpty(deptIds)) {
            return Collections.emptyList();
        }
        return userMapper.selectListByDeptIds(deptIds);
    }

    @Override
    public List<AdminUserDO> getUserListByPostIds(Collection<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyList();
        }
        Set<Long> userIds = convertSet(userPostMapper.selectListByPostIds(postIds), UserPostDO::getUserId);
        if (CollUtil.isEmpty(userIds)) {
            return Collections.emptyList();
        }
        return userMapper.selectByIds(userIds);
    }

    @Override
    public List<AdminUserDO> getUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return tkTenantAccessService.executeCurrentTenantForNonPlatform(() -> userMapper.selectByIds(ids));
    }

    public List<AdminUserDO> getUserListAll() {
        return userMapper.selectList();
    }


    @Override
    public void validateUserList(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 获得岗位信息
        List<AdminUserDO> users = userMapper.selectByIds(ids);
        Map<Long, AdminUserDO> userMap = CollectionUtils.convertMap(users, AdminUserDO::getId);
        // 校验
        ids.forEach(id -> {
            AdminUserDO user = userMap.get(id);
            if (user == null) {
                throw exception(USER_NOT_EXISTS);
            }
            if (!CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
                throw exception(USER_IS_DISABLE, user.getNickname());
            }
        });
    }

    @Override
    public List<AdminUserDO> getUserListByNickname(String nickname) {
        return userMapper.selectListByNickname(nickname);
    }

    private AdminUserDO validateUserForCreateOrUpdate(Long id, String username, String mobile, String email) {
        // 关闭数据权限，避免因为没有数据权限，查询不到数据，进而导致唯一校验不正确
        return DataPermissionUtils.executeIgnore(() -> {
            // 校验用户存在
            AdminUserDO user = validateUserExists(id);
            // 校验用户名唯一
            validateUsernameUnique(id, username);
            // 校验手机号唯一
            validateMobileUnique(id, mobile);
            // 校验邮箱唯一
            validateEmailUnique(id, email);
            return user;
        });
    }

    private void removeDeptAndPost(AdminUserDO user) {
        user.setDeptId(null);
        user.setPostIds(Collections.emptySet());
    }

    private void prepareTkUserScopeForSave(AdminUserDO user, AdminUserDO oldUser, boolean allowTenantAdmin) {
        if (StrUtil.isBlank(user.getTkUserLevel())) {
            user.setTkUserLevel(TK_USER_LEVEL_DEFAULT);
        }
        user.setTkUserLevel(normalizeTkUserLevel(user.getTkUserLevel()));
        if (!TK_PLATFORM_ADMIN.equals(user.getTkUserLevel())) {
            user.setTkCompanyId(null);
        }
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (isTkPlatformAdmin(loginUser)) {
            if (oldUser != null) {
                user.setTenantId(oldUser.getTenantId());
            }
            if (TK_PLATFORM_ADMIN.equals(user.getTkUserLevel())) {
                user.setTenantId(0L);
            } else {
                if (user.getTenantId() == null || user.getTenantId() <= 0) {
                    throw exception(USER_TK_TENANT_REQUIRED);
                }
                if (tenantService.getTenant(user.getTenantId()) == null) {
                    throw exception(TENANT_NOT_EXISTS);
                }
            }
            if (TK_TENANT_ADMIN.equals(user.getTkUserLevel()) && oldUser == null && !allowTenantAdmin) {
                throw exception(USER_TK_TENANT_ADMIN_FORBIDDEN);
            }
            if (TK_TENANT_ADMIN.equals(user.getTkUserLevel()) && oldUser != null
                    && !TK_TENANT_ADMIN.equals(oldUser.getTkUserLevel())) {
                throw exception(USER_TK_TENANT_ADMIN_FORBIDDEN);
            }
            return;
        }
        if (oldUser != null) {
            user.setTkUserLevel(oldUser.getTkUserLevel());
            return;
        }
        if (TK_PLATFORM_ADMIN.equals(user.getTkUserLevel())) {
            throw exception(USER_TK_PLATFORM_ADMIN_FORBIDDEN);
        }
        if (TK_TENANT_ADMIN.equals(user.getTkUserLevel())) {
            throw exception(USER_TK_TENANT_ADMIN_FORBIDDEN);
        }
    }

    private String normalizeTkUserLevel(String value) {
        if (TK_COMPANY_ADMIN.equals(value)) {
            return TK_TENANT_ADMIN;
        }
        if (TK_COMPANY_USER.equals(value)) {
            return TK_TENANT_USER;
        }
        return value;
    }

    private boolean isTkPlatformAdmin(LoginUser loginUser) {
        return loginUser != null && loginUser.getInfo() != null
                && TK_PLATFORM_ADMIN.equals(loginUser.getInfo().get(INFO_KEY_TK_USER_LEVEL));
    }

    @VisibleForTesting
    AdminUserDO validateUserExists(Long id) {
        if (id == null) {
            return null;
        }
        AdminUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    @VisibleForTesting
    void validateUsernameUnique(Long id, String username) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        AdminUserDO user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_USERNAME_EXISTS);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_USERNAME_EXISTS);
        }
    }

    @VisibleForTesting
    void validateEmailUnique(Long id, String email) {
        if (StrUtil.isBlank(email)) {
            return;
        }
        AdminUserDO user = userMapper.selectByEmail(email);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_EMAIL_EXISTS);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_EMAIL_EXISTS);
        }
    }

    @VisibleForTesting
    void validateMobileUnique(Long id, String mobile) {
        if (StrUtil.isBlank(mobile)) {
            return;
        }
        AdminUserDO user = userMapper.selectByMobile(mobile);
        if (user == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的用户
        if (id == null) {
            throw exception(USER_MOBILE_EXISTS);
        }
        if (!user.getId().equals(id)) {
            throw exception(USER_MOBILE_EXISTS);
        }
    }

    /**
     * 校验旧密码
     * @param id          用户 id
     * @param oldPassword 旧密码
     */
    @VisibleForTesting
    void validateOldPassword(Long id, String oldPassword) {
        AdminUserDO user = userMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (!isPasswordMatch(oldPassword, user.getPassword())) {
            throw exception(USER_PASSWORD_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // 添加事务，异常则回滚所有导入
    public UserImportRespVO importUserList(List<UserImportExcelVO> importUsers, boolean isUpdateSupport) {
        // 1.1 参数校验
        if (CollUtil.isEmpty(importUsers)) {
            throw exception(USER_IMPORT_LIST_IS_EMPTY);
        }
        // 1.2 初始化密码不能为空
        String initPassword = configApi.getConfigValueByKey(USER_INIT_PASSWORD_KEY);
        if (StrUtil.isEmpty(initPassword)) {
            throw exception(USER_IMPORT_INIT_PASSWORD);
        }

        // 2. 遍历，逐个创建 or 更新
        UserImportRespVO respVO = UserImportRespVO.builder().createUsernames(new ArrayList<>())
                .updateUsernames(new ArrayList<>()).failureUsernames(new LinkedHashMap<>()).build();
        AtomicInteger index = new AtomicInteger(1);
        importUsers.forEach(importUser -> {
            int currentIndex = index.getAndIncrement();
            // 2.1.1 校验字段是否符合要求
            try {
                ValidationUtils.validate(BeanUtils.toBean(importUser, UserSaveReqVO.class).setPassword(initPassword));
            } catch (ConstraintViolationException ex) {
                String key = StrUtil.blankToDefault(importUser.getUsername(), "第 " + currentIndex + " 行");
                respVO.getFailureUsernames().put(key, ex.getMessage());
                return;
            }
            // 2.1.2 校验，判断是否有不符合的原因
            try {
                validateUserForCreateOrUpdate(null, null, importUser.getMobile(), importUser.getEmail());
            } catch (ServiceException ex) {
                respVO.getFailureUsernames().put(importUser.getUsername(), ex.getMessage());
                return;
            }

            // 2.2.1 判断如果不存在，在进行插入
            AdminUserDO existUser = userMapper.selectByUsername(importUser.getUsername());
            if (existUser == null) {
                AdminUserDO createUser = BeanUtils.toBean(importUser, AdminUserDO.class)
                        .setPassword(encodePassword(initPassword)).setPostIds(new HashSet<>());
                removeDeptAndPost(createUser);
                prepareTkUserScopeForSave(createUser, null, false);
                tkTenantAccessService.applyCurrentTenantForNonPlatform(createUser);
                userMapper.insert(createUser); // 设置默认密码及空岗位编号数组
                respVO.getCreateUsernames().add(importUser.getUsername());
                return;
            }
            // 2.2.2 如果存在，判断是否允许更新
            if (!isUpdateSupport) {
                respVO.getFailureUsernames().put(importUser.getUsername(), USER_USERNAME_EXISTS.getMsg());
                return;
            }
            AdminUserDO updateUser = BeanUtils.toBean(importUser, AdminUserDO.class);
            updateUser.setId(existUser.getId());
            removeDeptAndPost(updateUser);
            prepareTkUserScopeForSave(updateUser, existUser, false);
            tkTenantAccessService.applyCurrentTenantForNonPlatform(updateUser);
            userMapper.updateById(updateUser);
            respVO.getUpdateUsernames().add(importUser.getUsername());
        });
        return respVO;
    }

    @Override
    public List<AdminUserDO> getUserListByStatus(Integer status) {
        return tkTenantAccessService.executeCurrentTenantForNonPlatform(() ->
                userMapper.selectListByStatus(status, getForcedTenantId()));
    }

    @Override
    public List<AdminUserDO> getDeptUsers(Collection<Long> deptIds) {
        return tkTenantAccessService.executeCurrentTenantForNonPlatform(() ->
                userMapper.selectListByDeptIds(deptIds, getForcedTenantId()));
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * 对密码进行加密
     *
     * @param password 密码
     * @return 加密后的密码
     */
    private String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    private Long getForcedTenantId() {
        return tkTenantAccessService.isCurrentUserPlatformAdmin() ? null : tkTenantAccessService.getCurrentUserTenantId();
    }

    private Long getRequestedTenantId(Long tenantId) {
        Long forcedTenantId = getForcedTenantId();
        return forcedTenantId != null ? forcedTenantId : tenantId;
    }

}
