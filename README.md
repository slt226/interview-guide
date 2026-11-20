<div align="center">

# InterviewGuide

**AI 模拟面试平台 · 二次开发版**

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0-6DB33F)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18.3-blue?logo=react)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-AGPL--3.0-red)](LICENSE)

</div>

---

## 关于本仓库

本仓库是 [JavaGuide 的 interview-guide](https://github.com/Snailclimb/interview-guide) 的**二次开发版本**，用于学习与求职展示。

上游已经实现了完整的业务闭环（简历解析、文字/语音面试、RAG 知识库、多模型接入）。我在其基础上补齐了三个上游没有覆盖的工程方向：**认证与多租户数据隔离**、**异步任务的可靠性闭环**、**死信运维能力**，并配套改造了前端。

> 这里刻意把「上游能力」和「本人新增」分开写。不是为了显得谦虚，而是因为**能说清楚哪些是自己做的、边界在哪，比把整个项目说成自己写的更经得起追问**。

## 与上游的差异

| 能力 | 上游 | 本仓库 |
|---|---|---|
| 认证鉴权 | 无（所有接口裸奔） | Spring Security + JWT 无状态认证，接口全量上锁 |
| 数据归属 | 无（登录后能读任意用户数据） | 5 张根表引入 `user_id`，子表外键继承，全链路归属校验 |
| 异步幂等 | 无并发保护 | `SET NX` 幂等占位 + 状态机原子领取 |
| 失败任务处理 | 仅日志 | 死信表归档 + 管理页重投 + 定时恢复 |
| 异步可观测性 | 无 | 4 个 Prometheus 指标 |
| 唯一约束 | 全局唯一 | 降级为租户内唯一（`user_id, file_hash`） |
| 前端 | 无登录 | 登录页 + 路由守卫 + 401 自动跳转 + 死信管理页 |

---

## 一、我做了什么

### 1.1 认证体系（`modules/auth`）

- **JWT 无状态认证**：HS256 签发，`subject` 存 userId，自定义 `username` claim 省一次查库；密钥长度不足 32 字节直接启动失败，避免上线后才发现签名弱。
- **自定义过滤器**：`JwtAuthenticationFilter extends OncePerRequestFilter`，验签成功才写入 `SecurityContext`；`SessionCreationPolicy.STATELESS`，服务端不存会话。
- **统一入口**：`CurrentUser.requireId()` 封装取当前用户的逻辑，业务代码不直接碰 Spring Security 的 API。
- **401 必须自己写 JSON**：Spring Security 默认 401 是空 body，前端解析出 `undefined` 只能报「网络错误」，因此通过 `authenticationEntryPoint` 手写 `{"code":401,...}`。
- **WebSocket 单独处理**：浏览器不允许给 WS 握手设置请求头，token 只能走 query 参数，由 `JwtHandshakeInterceptor` 验签后塞进 `session.getAttributes()`。

### 1.2 多租户数据隔离

**核心认知：认证只解决「你是谁」，不解决「数据属于谁」。**

- 迁移 `V20261110__add_user_id_for_data_isolation.sql`：`resumes`、`knowledge_bases`、`interview_sessions`、`rag_chat_session`、`interview_schedule` 五张根表加 `user_id bigint NOT NULL`，子表通过外键继承归属，不重复加列。
- **唯一键必须降级**：`(file_hash)` → `(user_id, file_hash)`、`(request_id)` → `(user_id, request_id)`。只改代码不改索引，B 传同一个文件就会撞 A 的记录。
- **防御性 404**：别人的资源与不存在的资源返回**完全一样**的错误码。如果「无权访问」单独报一个码，攻击者能通过错误码差异枚举出哪些 ID 真实存在。
- **幂等 key 必须带用户前缀**：Redis key 使用 `userId:requestId`，DB 查询用 `findOwnedByRequestId`。只改数据库唯一索引、不改 Redis key，照样越权。
- **异步消费者刻意不过滤**：消费者是系统身份、没有登录态，一刀切全加 `user_id` 会导致异步任务永远查不到数据。边界是：**用户发起的入口必须过滤，系统内部的处理不过滤。**

### 1.3 异步任务可靠性（`common/async`）

**核心论点：Redis Stream 是 at-least-once 语义，叠加 XAUTOCLAIM 对超时未 ACK 消息的重投，重复消费是必然事件，不是概率事件。一次语音评估约 ¥0.26，重复执行就是真实的钱。**

| 层 | 机制 | 解决什么 |
|---|---|---|
| 1 回收 | `XAUTOCLAIM`，pending 超 5 分钟重新认领 | 消费者进程崩溃，消息卡在 pending 无人处理 |
| 2 重试 | 最多 3 次，指数退避 | 大模型偶发超时、网络抖动 |
| 3 兜底 | 重试耗尽 → 标记业务失败 + 写死信表 | 永久失败不阻塞队列，且不丢现场 |
| 4 恢复 | 定时扫描 QUEUED>2min / PROCESSING>20min | 投递成功但无人消费的僵尸任务 |
| 5 幂等 | `SET NX` 占位 + 状态机原子领取 | 重复投递导致重复调大模型 → 重复计费 |

已知缺陷（主动记录，不是隐藏）：幂等键在失败时需主动删除，否则重试会被自己挡住；而「删除」本身不是原子操作，极端情况下仍有窗口。更严谨的做法是分布式锁 + 业务表唯一键双保险。

**死信闭环**：死信表 + 分页查询接口 + 前端管理页 + 手动重投。重投会重新投递到原 Stream，而不是直接执行业务逻辑。

### 1.4 可观测性

`/actuator/prometheus` 暴露：

| 指标 | 含义 |
|---|---|
| `app.async.idempotency.hits` | 幂等拦截次数 |
| `app.async.dead_letter.archived` | 死信归档数 |
| `app.async.dead_letter.replayed` | 死信重投次数 |
| `app.async.dead_letter.pending` | 当前待处理死信数 |

> 命名踩过的坑：**不要用 `_created` / `_total` / `_count` 这类 Prometheus 保留后缀**，Micrometer 会剥离或改写（实测 `_created` 被改写成 `_total`）。

### 1.5 改造规模

| 项 | 数量 |
|---|---|
| 改动文件 | 79 个 |
| 新增代码 | 1649 行 / 删除 458 行 |
| 新增 Java 类 | 20 个 |
| 新增前端模块 | 9 个（登录页、死信页、路由守卫、API、类型） |
| 新增迁移脚本 | 3 个 |
| 单元测试 | 310 个（全绿，46 个集成测试按外部依赖跳过） |

---

## 二、上游已有能力（非本人实现）

以下为上游 interview-guide 的原生能力，本仓库保留未改或仅做适配：

| 模块 | 能力 |
|---|---|
| 简历管理 | PDF/DOCX/TXT 解析、异步 AI 分析、内容哈希去重、PDF 报告导出 |
| 文字面试 | Skill 驱动出题（11 套岗位技能包）、智能追问、分批评估、报告导出 |
| 语音面试 | WebSocket + 千问3 ASR/TTS、服务端 VAD 断句、句子级并发 TTS |
| 知识库 | 文档分块、1024 维向量化、pgvector HNSW 检索、查询改写、SSE 流式问答 |
| 知识库题库 | 从文档生成题目、容量校验、专项面试、评估闭环 |
| 面试日程 | 飞书/腾讯会议/Zoom 邀请解析、日历视图、状态流转 |
| 多模型 | DashScope / Kimi / DeepSeek / GLM / LM Studio 热切换，API Key AES-GCM 加密 |
| 限流 | `@RateLimit` 可重复注解，Global / IP / User 三维度，Redis Lua 原子计数 |

---

## 三、技术栈

| 层 | 选型 |
|---|---|
| 语言 / 框架 | Java 25（虚拟线程）· Spring Boot 4.1 · Spring AI 2.0 |
| 数据库 | PostgreSQL 16 + pgvector（1024 维，HNSW，COSINE） |
| 缓存 / 消息 | Redis 6+ · Redisson · Redis Stream |
| 对象存储 | S3 兼容（开发用 RustFS，部署用 MinIO） |
| 文档解析 / 导出 | Apache Tika · iText 8 |
| 认证 | Spring Security · JJWT（HS256）· BCrypt |
| 迁移 / 文档 | Flyway（8 个版本脚本）· SpringDoc OpenAPI |
| 前端 | React 18 · TypeScript 5.6 · Vite · Tailwind CSS 4 |

---

## 四、快速开始

### 环境要求

| 依赖 | 版本 | 说明 |
|---|---|---|
| JDK | 25 | 必需 |
| Node.js | 18+ | 前端构建 |
| pnpm | 10+ | 前端包管理器 |
| Docker | - | 推荐，一键起依赖服务 |

### 1. 克隆

```bash
git clone https://github.com/slt226/interview-guide.git
cd interview-guide
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

最少需要填写两项：

```ini
AI_BAILIAN_API_KEY=your_dashscope_api_key
APP_AI_CONFIG_ENCRYPTION_KEY=your_random_long_secret
```

- `AI_BAILIAN_API_KEY`：[阿里云百炼](https://bailian.console.aliyun.com/)申请，文本模型与 ASR/TTS 共用。
- `APP_AI_CONFIG_ENCRYPTION_KEY`：AES-GCM 加密 API Key 落库用的主密钥。**一旦更换，历史密文全部无法解密**，需用旧密钥解密后重新加密迁移。

> 注意：运行时 Provider 配置从**数据库**读取，改 `.env` 只影响首次初始化。已启动过的实例要换 Key，请在设置页修改或直接改库。

### 3. 启动依赖服务

```bash
docker compose -f docker-compose.dev.yml up -d
```

| 服务 | 地址 | 账号 |
|---|---|---|
| PostgreSQL | `localhost:5432` | `postgres` / `123456` |
| Redis | `localhost:6379` | - |
| RustFS 控制台 | `localhost:9001` | `rustfsadmin` / `rustfsadmin` |

Bucket 由应用启动时自动创建（`storage.auto-create-bucket: true`），无需手动建。

### 4. 启动应用

```bash
# 后端（会自动执行 Flyway 迁移）
./gradlew.bat :app:bootRun        # Windows
./gradlew :app:bootRun            # macOS / Linux

# 前端
cd frontend && pnpm install && pnpm dev
```

| 服务 | 地址 |
|---|---|
| 前端 | http://localhost:5173 |
| 后端 | http://localhost:8080 |
| 接口文档 | http://localhost:8080/swagger-ui.html |

> **在 IDEA 里直接 Run `App.main()` 会启动失败**：根目录 `.env` 只被 Gradle 的 `bootRun` 任务注入。请在 IDEA 中新建 **Gradle** 类型运行配置，Task 填 `:app:bootRun`。

### 5. 首次使用

1. 打开 http://localhost:5173 ，进入登录页注册账号（密码 BCrypt 加密存储）。
2. 登录后进入设置页，确认 Provider 与 Key 可用（点「测试连接」会发真实请求）。
3. 上传一份简历 → 等待异步分析完成 → 开始面试。

---

## 五、测试

```bash
./gradlew :app:test --no-daemon
```

| 项 | 数值 |
|---|---|
| 用例总数 | 310 |
| 失败 | 0 |
| 跳过 | 46（依赖外部服务的集成测试） |

写单测踩过的坑，记在这里省得后人再踩：

- **纯 Mockito 测试没有过滤器链**，`SecurityContextHolder` 为空，加了 `CurrentUser.requireId()` 后所有用例齐刷刷报 401。解决方式是 `TestAuthContext.login(1L)` 在 `@BeforeEach` 手动注入上下文。
- **`processMessage` 有 SET NX 消费幂等**，mock 默认返回 `false` 会被当成重复投递直接 ACK，表现为 `verify` 报「zero interactions」。setUp 里要桩 `trySetIfAbsent(...) → true`。

---

## 六、已知问题与后续计划

**已知问题**

- JWT 无 refresh 机制、无黑名单，token 到期前无法主动失效（删除账号后旧 token 仍可用至过期）。
- 登录接口没有失败次数限制，可被暴力尝试（限流目前只挂在知识库问答上）。
- 错误码风格未统一：知识库详情返回 `6001`，问答接口返回 `404`，安全上等价但不够一致。
- RAG 未做 rerank，数据量增大后召回质量会下降。

**后续计划**

- [ ] 引入 Redis 黑名单 / refresh token，支持主动登出
- [ ] 登录接口接入限流与失败锁定
- [ ] RAG 增加 rerank 阶段
- [ ] 异步框架的幂等键改为「分布式锁 + 业务唯一键」双保险
- [ ] 补充 CI 与压测（当前只有单测）

---

## 七、效果展示

建议自行截图补充。**不要直接引用上游仓库的截图**，那是他人的演示环境，放在自己的仓库里既容易失效也不合适。

建议至少补三张：登录页、面试进行中、死信管理页。

---

## 八、致谢与许可

- 上游项目：[Snailclimb/interview-guide](https://github.com/Snailclimb/interview-guide)，本项目基于其 AGPL-3.0 许可二次开发。
- 本仓库新增代码同样遵循 **AGPL-3.0**：只要通过网络提供服务，就必须向用户公开修改后的源码。

如果这个项目对你有帮助，欢迎给上游点个 star。
