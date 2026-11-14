# 手机号验证、在线充值与租户邀请码实施计划

日期：2026-11-14。状态：需求分析完成，等待开发确认。

本轮只形成计划，不修改业务代码、不提交发布、不调用真实短信或支付。

# 一、需求理解

我的目标：让新用户完成手机号验证后自助开户，按当前充值套餐在线购买积分；每个业务租户拥有独立邀请码，注册时可选填写，记录推广来源。

实际需要解决的问题：补齐“验证手机号 → 创建独立租户及管理员 → 记录邀请来源 → 在线支付 → 租户积分到账”的完整链路，并确保失败可恢复、数据不串租户、回调不重复加分。

已确认的产品规则：邀请码用于推广归因。受邀者创建自己的租户，邀请者没有其数据访问权。第一期只记录归因，不增加返佣、赠送积分或优惠。

## 源码核查结论

1. TK 当前登录页只挂载登录、SSO、找回密码表单，没有挂载现存 RegisterForm，也没有注册按钮。
2. TK 现有 `/system/auth/register` 是向当前租户增加用户，不是创建独立租户；请求只有账号、昵称、密码及图形验证码，没有手机号、短信验证码或邀请码。
3. TK 现有发送验证码服务会先要求手机号对应的用户已存在，不能原样用于新用户注册。基础配置还存在固定测试验证码，不能据此判断生产配置。
4. 即映已有 `/register/sms-code`，但它会查询即映自己的手机号占用情况；验证码消费在 `SysRegisterService` 调用的内部方法中，没有独立的跨产品验证 API。直接调用其发码接口不能完成 TK 的注册验证。
5. 即映短信实际通过阿里云发送，已经具备 Redis 原子消费、错误次数限制、手机号/IP/全局限流等能力，可以复用其服务设计与供应商适配。
6. 即映支付由其后端调用 Saas发布 `/api/payments/jiying/*`，使用独立签名、Stripe 商户、订单、回调和补偿任务；积分在即映自己的数据库记账。
7. Saas发布现有通道写死了即映产品标识、订单格式、签名配置、通知地址和数据库表，不能把 TK 的订单直接混入这个通道。
8. TK 已有租户积分账户、积分流水和管理员充值接口，可以复用积分账户与扣减链路，但尚缺本次在线支付所需的订单关联与到账幂等。
9. 当前 `system_tenant.account_count` 在 TK 页面表示积分额度，同时仍被用户服务当作账号数量上限。零积分开户、在线充值和成员数量需要在本次相关入口中解除这种耦合。
10. 当前充值页面说明包含“6 个月有效期、7 天未使用可申请退款”；已核查的账户及流水结构没有积分批次到期字段，接入支付时不能只保留文案。

上述结论来自本地源码，不代表已经验证生产开关、供应商资质或真实交易连通性。

# 二、需要增加的内容

## 产品功能

1. 注册入口：工作空间名称、账号、昵称、密码、手机号、短信验证码、邀请码（选填）。
2. 短信验证通过后创建独立租户及租户管理员；初始付费积分为 0。
3. 每个业务租户一个随机邀请码，支持复制邀请码和带码注册链接；已有业务租户补发邀请码。
4. 邀请码留空正常注册；填写无效、禁用或不存在的码时明确提示，用户可以清空后继续。
5. 邀请关系只在注册成功时记录一次，禁止注册后随意改绑；邀请者只能查看允许展示的统计和脱敏信息。
6. 套餐购买、支付状态、充值记录、到账后余额刷新。
7. 保留当前套餐的整数积分数，不根据折扣文案重新计算：

| 套餐编码 | 人民币金额 | 到账积分 |
| --- | ---: | ---: |
| P1000 | 1,000 元 | 250 |
| P5000 | 5,000 元 | 1,333 |
| P10000 | 10,000 元 | 3,077 |
| P50000 | 50,000 元 | 16,667 |

8. 新购积分自支付成功时间起有效 6 个日历月；第一期保留人工受理退款，系统提供订单、积分使用情况和安全冻结/扣回能力。自助退款页面列入 P1。
9. 默认购买权限给租户管理员，普通成员继续使用共享积分池。邀请码默认给租户管理员展示。

## 前端

页面：启用注册表单；保留工作台充值说明入口并增加购买；新增充值记录页面、我的邀请页面及必要的管理端查询入口。

组件：短信验证码输入和重发倒计时、套餐卡片、支付弹窗、支付结果状态、邀请码及邀请链接复制。

交互：链接中的邀请码预填但允许删除；短信发送失败不显示成功；切换手机号清理旧挑战；支付中显示查询中/待支付/到账中/已到账/关闭/异常；重复点击恢复原订单；支付返回页面后向本系统查询实际到账状态。

## 后端

接口规划（均为拟新增接口，不宣称已经存在）：

| 系统 | 接口 | 用途与权限 |
| --- | --- | --- |
| 即映 | `POST /internal/phone-verification/send` | TK 后端签名调用，返回挑战编号和有效期 |
| 即映 | `POST /internal/phone-verification/consume` | TK 后端签名调用，按注册请求号幂等消费 |
| TK | `POST /system/auth/tenant-register/sms-code` | 注册发码，图形验证和限流 |
| TK | `POST /system/auth/tenant-register` | 手机验证、独立租户开户及推广绑定 |
| TK | `GET /system/tenant-invite/my` | 当前租户管理员读取邀请码 |
| TK | `GET /system/tenant-invite/records` | 当前租户管理员读取脱敏邀请记录 |
| TK | `GET /tk/recharge/packages` | 获取套餐和可用支付渠道 |
| TK | `POST /tk/payments/quote` | 服务器确定用户、租户及套餐并生成本地订单 |
| TK | `POST /tk/payments/{orderNo}/pay` | 确认报价并创建或恢复付款 |
| TK | `GET /tk/payments/{orderNo}` | 当前租户订单状态 |
| TK | `GET /tk/payments` | 当前租户充值记录 |
| TK | `POST /tk/payments/{orderNo}/cancel` | 安全关闭未付款订单 |
| TK | `POST /tk/payments/notify` | 只接受 Saas发布签名通知 |
| Saas发布 | `/api/payments/clipforge/*` | TK 专用目录、报价、下单、查单、取消、退款、渠道 webhook 和返回地址 |

服务：即映跨产品短信适配；TK 可信租户开户服务、邀请码服务、支付网关适配、支付订单服务、积分批次服务。

任务：本地查单补偿、Saas发布渠道查单及通知重试、积分到期处理；按到期时间索引分批执行并避免多实例重复领取。

## 数据库

TK MySQL 新增表：

| 表 | 核心字段 | 关键索引/约束 |
| --- | --- | --- |
| `system_tenant_registration` | 注册请求号、验证手机号、租户、管理员、状态、验证挑战、创建时间 | 请求号唯一；自助开户手机号唯一 |
| `system_tenant_invite_code` | 租户、随机邀请码、启用状态 | 租户唯一；邀请码全局唯一 |
| `system_tenant_referral` | 邀请租户、被邀请租户、注册用户、邀请码快照、注册时间 | 被邀请租户唯一；邀请租户+时间索引 |
| `tk_payment_order` | 订单号、租户、购买人、套餐快照、人民币分、实际付款最小币种单位、币种、积分、报价期限、支付标识、支付状态、积分到账状态、通知版本 | 订单号唯一；渠道支付号唯一；租户+幂等键唯一；租户+时间及状态+下次查询时间索引 |
| `tk_payment_event` | 通知事件号/版本、原订单、处理结果及时间 | 事件唯一；订单+版本唯一 |
| `tk_credit_batch` | 来源订单/管理员充值、租户、批次积分、可用/冻结/已用/过期/退款积分、到账与到期时间 | 来源订单唯一；租户+到期时间索引 |
| `tk_credit_batch_allocation` | 积分流水、批次、分配积分、冻结/结算/退回状态 | 流水+批次唯一 |

现有表扩展：`system_tenant.member_limit` 独立管理成员上限；保持 `account_count` 的现有 TK 积分兼容映射。积分账户、流水沿用现有表，不建设第二套余额。

Saas发布 PostgreSQL 新增 `clipforge_payment_order`、`clipforge_payment_event`、`clipforge_payment_outbox`，沿用即映通道的事务/RLS/通知箱模式，独立产品标识、唯一索引和服务端权限。

即映：新服务的挑战、错误次数、限流、签名 nonce、消费结果均使用独立 Redis 命名空间和有效期；不共用即映注册挑战，也不跨产品查询手机号占用。

## 第三方服务

1. 短信：复用即映阿里云发送适配，给 TK 配置允许使用的签名、模板、独立 appId、调用密钥、限流预算和开关。TK 不持有阿里云密钥。
2. 支付：复用 Saas发布现有 Stripe 商户和 SDK；增加 TK 专用请求密钥、通知密钥、webhook 签名密钥、回调域名、返回地址、支付方式和 live/test 开关。
3. 阿里云签名/模板适用范围，以及 Stripe 账户实际可用支付方式与币种，在联调前核实。不能将即映能够使用等同于 TK 配置已就绪。
4. 密钥仅服务端保存；短信和支付使用不同密钥，两个产品也使用不同密钥。

# 三、实现方案

## 第一步：明确配置和数据库迁移

建立接口契约和失败状态，增加注册/短信/支付开关。默认租户权限套餐 ID、租户有效天数作为必填配置；配置缺失时不开启自助注册，不自动使用系统最高权限套餐。建议新开户成员上限默认 1，后续由管理员调整；历史租户按迁移时原上限与实际成员数的较大值回填。

邀请码语义已确认，不再作为租户权限选择参数。现有用户名和租户名称校验保留。

## 第二步：即映补充跨产品手机号验证接口

推荐方案：在即映现有后端中增加独立的服务间入口和验证码服务，复用 AliyunSmsClient 及现有 Redis 原子处理思路。服务端固定 appId 对应的模板、用途和额度。验证码绑定 appId、手机号、用途、挑战号；不绑定即映用户表。

发送接口先验签、校验时间戳及 nonce，再执行幂等与限流。默认 6 位安全随机码、5 分钟有效、60 秒重发间隔、最多 5 次错误，手机号/IP/应用额度均由服务端限制。最终额度可在配置中收紧。

消费接口携带注册请求号，同一请求成功后可幂等返回相同结果；不同手机号、appId 或用途不能复用。TK 必须据服务端结果开户，不接受浏览器传来的“已验证”标记。远端验证后本地事务失败，允许同一注册请求恢复，不能直接放行失效验证码。

备选方案：TK 通过现有短信框架直接接阿里云，改动跨项目依赖较少，但需在 TK 独立配置供应商凭证与验证码能力。此次按用户希望复用即映服务的方向推荐前述方案。

直接调用即映 `/register/sms-code` 不作为交付方案，因为缺少独立消费接口并混入即映账号占用检查。

## 第三步：可信开户与邀请码

新增自助租户注册接口，与现有“当前租户注册用户”接口区分；旧匿名注册入口保持关闭，避免绕过手机号校验。

先校验注册参数、邀请码和默认套餐，再幂等消费手机号挑战；在本地事务中创建租户、可信租户管理员、默认角色权限、开户记录、当前租户邀请码和可选的推广关系。使用当前项目的多数据源事务惯例保证一致性。成功后在新租户上下文签发已有格式的登录令牌，前端保存新租户信息。

可信开户只能由内部服务设置 TENANT_ADMIN；不能简单放开现有管理员等级校验，也不能接受浏览器提供的平台管理员等级、目标租户 ID、积分数或菜单权限。

自助开户手机号唯一性由 TK 自己持久化约束保证；同一个手机号允许独立使用即映。已有重复手机号不自动合并、覆盖或迁移归属，联调前输出冲突列表供处理。

每个业务租户生成 10 位随机字母数字邀请码；唯一冲突自动重试。排除平台系统租户。历史业务租户幂等分批补发。禁止自邀、重复绑定与跨租户读取完整用户资料。

## 第四步：Saas发布增加 TK 支付通道

新增 `/api/payments/clipforge/*`、独立合同定义、独立表及 worker 周期。复用已有 SDK、报价和补偿处理方式，不直接修改即映通道或把它整体改造成新支付平台。

订单使用 `TKP_` 前缀，Stripe metadata 使用 `product=clipforge`。支付的业务归属为 TK 的 tenantId、userId、orderNo，不把 TK 数字 ID 关联到 Saas发布的登录账户或积分余额。

套餐目录由 Saas发布服务端固定配置并提供给 TK；TK 保存下单时的版本和快照，前端不再独立硬编码金额和积分。支付方式只展示实际配置可用的渠道；涉及币种换算时展示实际币种、付款金额及报价期限，失败时拒绝猜测金额。

## 第五步：本地订单、到账与积分批次

TK 先持久化订单及幂等键，再请求报价及付款；超时查询/恢复同一订单，禁止因重试创建第二笔付款。

通知先验签与防重放，再由 TK 后端主动向支付服务核对当前状态、产品、模式、金额、币种、用户、租户及套餐。网页跳回和前端“付款成功”都不作为入账依据。

本地事务锁定订单，只有未入账的有效已支付订单能够增加积分、创建唯一来源批次、写入积分流水并标记已到账。失败整体回滚；worker 后续恢复。重复通知、乱序通知和并发查单都不能多记积分。

积分账户仍作为现有聚合余额；批次记录来源、消耗与到期，冻结/结算/任务失败退回都关联批次。新购积分按最早到期优先扣减；冻结中的积分到期后，成功任务照常结算，失败释放回原批次，已经到期的部分不恢复为可用余额。

历史剩余额度按原记录作为旧余额批次导入，不追溯扣除或凭空设定过期时间。管理员充值同样走有来源的批次账本。手动调整积分和现有同步逻辑需要统一进入该记账入口，避免批次与聚合余额相互覆盖。

退款按现有页面条件人工受理，先原子冻结该订单尚未使用的全部积分，再请求原订单原币种全额退款；处理中保持冻结，确认成功后扣回，明确失败后释放。外部退款、部分退款、积分已消费等异常进入人工复核，不能自动再次加分或强行清空账户。

## 第六步：前端联调与验收

启用注册表单及状态切换，增加短信验证码和可选邀请码。工作台增加套餐选择和支付弹窗；查询状态有间隔和次数限制，关闭页面后由后台任务继续处理。充值记录区分支付成功与积分到账状态。邀请页面只展示本租户允许查看的信息。

修改现有文件的原因：复用当前登录状态、租户创建、角色权限、积分账户和工作台交互。

新增文件的原因：把外部适配、公开开户、支付状态机和批次记账按职责拆开，控制对已有代码的影响。

删除文件：无。部署脚本或服务器变更另待明确部署指令。

# 四、技术方案

架构：保留 Spring Boot/MySQL/Redis + Vue 前端。即映提供服务端手机号验证；Saas发布提供独立 TK 支付通道；身份、租户、邀请码关系和积分余额由 TK 管理。

数据流：

- 手机验证：浏览器 → TK 注册发码接口 → 即映 appId 隔离短信服务 → 阿里云 → 手机。
- 注册开户：浏览器提交手机号/挑战/验证码/可选邀请码 → TK 校验 → 即映幂等消费 → TK 创建独立租户、管理员、邀请码及推广关系 → 返回新租户登录令牌。
- 在线充值：浏览器选择套餐 → TK 创建订单 → Saas发布报价/付款 → Stripe → Saas发布验签查单并写通知箱 → TK 核对并事务入账 → 浏览器查询后刷新余额。

调用流程：注册与支付均使用稳定业务请求号。服务间 HTTPS + 原始请求体 HMAC + 时间戳 + nonce；通知使用独立密钥。租户归属从登录身份或本地订单读取，不能信任浏览器和回调临时提交的 tenantId。

故障恢复：短信同请求重放结果；注册同请求恢复；支付超时查询原单；通知持久化重试；TK 主动查单兜底；上线开关关闭新购买后仍继续处理历史订单。

# 五、开发任务拆解

## 文件定位约定

以下路径前缀对应真实工作区，文件位置均相对于对应前缀拼接：

- `TK`：`C:/Users/lhd/Documents/TK自动混剪SaaS产品/ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master`
- `SYSTEM`：`TK/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system`
- `BIZ`：`TK/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk`
- `WEB`：`TK/yudao-ui/yudao-ui-admin-vue3/src`
- `JIYING`：`C:/Users/lhd/Documents/ChatGPT/即映`
- `SAAS`：`C:/Users/lhd/Documents/ChatGPT/Saas发布`

以下新增文件名是本计划的建议命名，不表示文件已经存在。各任务按顺序执行，不需要同时改动三个项目的全部模块。

## Task 1：契约、配置和开户基础

目标：固定短信、注册、邀请码和支付请求/响应及失败状态；给零积分开户提供正确的成员上限。

文件：修改 `SYSTEM/dal/dataobject/tenant/TenantDO.java`、`SYSTEM/service/tenant/TenantServiceImpl.java`、`SYSTEM/service/user/AdminUserServiceImpl.java`、`SYSTEM/controller/admin/tenant/vo/tenant/TenantSaveReqVO.java`、`SYSTEM/controller/admin/tenant/vo/tenant/TenantRespVO.java`、`WEB/api/system/tenant/index.ts`、`WEB/views/system/tenant/TenantForm.vue`、`TK/yudao-server/src/main/resources/application.yaml`；新增 `TK/yudao-module-tk/src/main/resources/sql/tk_registration_invite_upgrade_mysql.sql`。

实现：新增独立 member_limit 和平台默认开户配置；迁移验证历史用户数及原上限；原 account_count 积分含义和接口字段保留。新匿名接口按最小路径处理租户过滤，不能扩大忽略整个业务前缀。

验收：零积分可创建首位管理员；加积分不扩大成员上限；原租户管理及权限检查继续有效。

## Task 2：即映跨产品短信与 TK 适配

目标：TK 可以安全调用即映发码和消费验证码。

文件：即映新增 `JIYING/ruoyi-admin/src/main/java/com/ruoyi/web/controller/internal/PhoneVerificationController.java`、`JIYING/ruoyi-framework/src/main/java/com/ruoyi/framework/web/service/ExternalPhoneVerificationService.java`、`JIYING/ruoyi-framework/src/main/java/com/ruoyi/framework/config/ExternalPhoneVerificationProperties.java` 和独立 Redis 脚本；修改 `JIYING/ruoyi-framework/src/main/java/com/ruoyi/framework/config/SecurityConfig.java` 仅放行到专用签名鉴权入口；复用现有 AliyunSmsClient。

TK 新增 `SYSTEM/service/auth/TkPhoneVerificationClient.java`、`SYSTEM/framework/phone/TkPhoneVerificationProperties.java`。

实现：appId 隔离、用途绑定、发送/消费幂等、错误次数与限流、签名防重放。外部服务不查询即映用户唯一性。消费重试结果限定在原注册请求。

验收：跨 appId/用途验证码失败、重复发送不重复计费、超限拒绝、同请求消费恢复、日志无验证码及密钥。

## Task 3：自助租户注册

目标：手机验证后创建独立租户并登录。

文件：修改 `SYSTEM/controller/admin/auth/AuthController.java`、`SYSTEM/service/auth/AdminAuthService.java`、`SYSTEM/service/auth/AdminAuthServiceImpl.java`，及 Task 1 的内部可信创建入口；新增 `SYSTEM/controller/admin/auth/vo/TkTenantRegisterReqVO.java`、`SYSTEM/service/auth/TkTenantRegistrationService.java`、`SYSTEM/dal/dataobject/tenant/TenantRegistrationDO.java`、`SYSTEM/dal/mysql/tenant/TenantRegistrationMapper.java`。

实现：分离新开户与旧租户内注册；创建角色和管理员使用服务端可信字段，返回现有 AuthLoginRespVO 格式；开户请求幂等和手机号唯一约束；本地失败可恢复。旧匿名注册保持关闭并覆盖绕过测试。

验收：手机号未经验证不能开户；失败无孤立租户；并发请求只创建一个租户；无法提交平台管理员角色；新令牌归属正确租户。

## Task 4：邀请码及推广归因

目标：每个业务租户有一码，注册可选绑定。

文件：新增 `SYSTEM/service/tenant/TenantInviteService.java`、`SYSTEM/controller/admin/tenant/TenantInviteController.java`、`SYSTEM/dal/dataobject/tenant/TenantInviteCodeDO.java`、`SYSTEM/dal/dataobject/tenant/TenantReferralDO.java`、`SYSTEM/dal/mysql/tenant/TenantInviteCodeMapper.java`、`SYSTEM/dal/mysql/tenant/TenantReferralMapper.java`；在 Task 3 注册事务中接入。

实现：随机唯一码、存量幂等回填、空码放行、无效码提示、一次归因、脱敏记录、服务端租户权限检查。

验收：不填可注册；有效码准确归因；邀请双方始终独立；邀请码冲突可恢复；不能改绑或跨租户查看记录。

## Task 5：Saas发布 TK 专用支付通道

目标：使用同一商户提供隔离的 TK 付款能力。

文件：新增 `SAAS/apps/web/app/api/payments/clipforge/[...path]/route.ts`、`SAAS/packages/billing/stripe/src/clipforge.ts`、`SAAS/packages/billing/stripe/src/clipforge-contract.ts`、`SAAS/apps/web/supabase/migrations/20261114000000_clipforge_payments.sql`，及对应 schema 镜像和测试；修改 `SAAS/packages/billing/stripe/package.json` 导出、`SAAS/apps/publish-worker/src/index.ts` 增加独立循环，并更新生成的数据库类型。

实现：目录、报价、下单、查单、取消、退款、独立 webhook；TKP_ 订单、clipforge 产品标识、独立密钥；渠道事件和通知箱事务一致；服务端专用 RLS 权限。schema 编号按该目录实施时实际顺序分配。

验收：金额和积分与四档套餐一致；请求重放拒绝；重复创建只得到同一付款；即映与 TK webhook 互不认领订单；普通数据库角色不可读写或清空支付表；worker 通知失败后可恢复。

## Task 6：TK 订单、积分到账与到期/退款账务

目标：一次付款只入账一次，积分可追溯来源及到期。

文件：新增 `BIZ/service/payment/TkPaymentGateway.java`、`BIZ/service/payment/TkPaymentService.java`、`BIZ/controller/admin/payment/TkPaymentController.java`、`BIZ/controller/open/payment/TkPaymentNotifyController.java`、`BIZ/dal/dataobject/TkPaymentOrderDO.java`、`BIZ/dal/mysql/TkPaymentOrderMapper.java`、`BIZ/job/TkPaymentReconcileJob.java`、`BIZ/service/credit/TkCreditBatchService.java`、`BIZ/job/TkCreditExpireJob.java`，及事件、批次、分配的 DO/Mapper；新增 `TK/yudao-module-tk/src/main/resources/sql/tk_online_payment_upgrade_mysql.sql`。

修改 `BIZ/service/credit/TkCreditService.java`、`BIZ/service/credit/TkCreditServiceImpl.java`、`BIZ/dal/mysql/TkTenantCreditAccountMapper.java` 及其直接依赖；补充支付/批次专用业务流水类型。

实现：服务器确定租户及用户；订单、批次、积分流水关联；行锁及唯一约束双重幂等；支付状态与到账状态分开；账户原有冻结、结算、任务失败退回与批次一致；管理员调整入口不能绕过批次；到期与退款按前述规则处理。

验收：重复/乱序通知、并发查单、入账中断、渠道超时、过期批次释放、退款与扣分并发均守住余额；原混剪扣分与失败返还回归通过。

## Task 7：前端注册、支付与邀请页面

目标：用户可以完成三个业务闭环。

文件：修改 `WEB/views/Login/Login.vue`、`WEB/views/Login/components/index.ts`、`WEB/views/Login/components/LoginForm.vue`、`WEB/views/Login/components/RegisterForm.vue`、`WEB/api/login/index.ts`、`WEB/api/login/types.ts`、`WEB/views/tk/dashboard/index.vue` 和对应中英文文案；新增 `WEB/api/tk/payment/index.ts`、`WEB/api/system/tenantInvite/index.ts`、`WEB/views/tk/recharge/components/PaymentDialog.vue`、`WEB/views/tk/recharge/index.vue`、`WEB/views/tk/invite/index.vue` 及对应菜单迁移。

实现：注册入口和图形/短信验证；邀请码可编辑；服务器套餐目录；可用渠道选择、报价确认、二维码/跳转、订单恢复、到账刷新、充值与邀请记录。工作台大文件只增加调用入口，新增支付交互放在独立组件。

验收：中英文、手机屏幕、刷新/返回、关闭弹窗、手机号切换、链接预填、无效码清空继续、普通成员权限及账务状态展示。

## Task 8：跨项目联调与上线前验收

目标：验证完整闭环和既有能力兼容性。

文件：三个项目新增的短信、注册、邀请、支付、积分服务测试及接口文档；不修改无关模块。

实现：先在测试环境模拟短信供应商、支付与重复回调，再接真实测试渠道。测试商户模式与正式商户模式明确隔离；正式小额验收需要限定测试套餐、测试用户与截止时间，不把临时测试套餐开放给普通租户。

TK 后端使用工作区 Maven 对 system/tk 相关用例定向运行；TK 前端运行 `pnpm ts:check` 及改动文件检查。即映沿用相关测试；Saas发布运行新通道合同/状态测试及现有即映回归，按其仓库要求执行类型、格式检查和数据库权限攻击测试。

联调验收不得只证明网页显示成功，必须核对支付服务订单、TK 本地订单、积分批次及账户余额四者一致。

# 六、风险分析

| 维度 | 风险 | 处理 |
| --- | --- | --- |
| 性能 | 短信刷量、页面高频查单、补偿扫描扩大 | 应用/手机号/IP 限流，前端退避，任务领取与分页索引 |
| 安全 | 伪造回调、重放、浏览器篡改金额或租户、匿名开户提权 | 服务间签名、防重放、固定目录、可信身份、最小公开路径、权限与 RLS 测试 |
| 数据 | 重复入账、跨服务半成功、邀请关系重复、积分账不平 | 稳定请求号、唯一约束、事务、状态版本、补偿、对账及审计 |
| 兼容性 | 旧注册绕过短信、account_count 混用、旧扣分不认识批次 | 保留旧路径并关闭旧匿名开户；成员上限分离；所有积分变更统一走批次适配 |
| 扩展性 | 两个产品订单和验证码互相影响 | 产品专用 appId、订单标识、密钥、表、回调与开关；复用适配，不做无关平台重构 |
| 可用性 | 即映不可用导致 TK 无法发送/校验短信；支付服务不可用 | 超时、清晰重试提示、同请求恢复；拒绝跳过手机号验证或猜测支付成功 |
| 商业规则 | 展示套餐与收费不一致，有效期/退款文案没有账务支持 | 目录和订单快照统一；补齐批次账；上线前核对渠道能力及页面承诺 |

这项工作涉及 TK、即映、Saas发布三个项目。仅部署 TK 的改动不能完成交付。当前计划不代表已获服务器部署或真实资金操作授权。

# 七、开发优先级

P0：必须完成

- 即映服务间短信接口、TK 可信注册和手机号校验、旧匿名入口防绕过。
- 独立租户管理员开户、初始零积分、独立成员上限、权限套餐及有效期配置。
- 每租户邀请码、注册可选归因、历史回填、租户隔离。
- 四档套餐与 Saas发布 TK 支付通道、订单幂等、回调核验、一次到账、补偿和记录。
- 积分批次、6 个月到期、人工退款的安全记账与异常处理；基本审计及验证。

P1：建议完成

- 自助退款申请和后台审批交互。
- 推广注册/首次付费统计、管理端按邀请来源筛选。
- 支付和短信异常聚合告警、人工对账处理页面。

P2：优化项

- 邀请海报、更多推广渠道统计。
- 更多支付方式或国际手机号支持。
- 返佣/奖励作为独立需求分析，避免自动引入结算规则。

# 八、等待确认

推荐执行顺序：Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7 → Task 8。

开发默认按本计划执行，关键新增配置包括默认租户权限套餐、租户有效天数、成员上限、TK 短信应用、支付商户模式及回调地址；配置不完整时保持相应开关关闭。

确认后最小修改、复用原有架构和接口，按任务验证；开发结果只输出 diff，不输出完整文件。

是否开始开发？等待确认。
