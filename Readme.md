ALSys 使用说明
本文档是我整理的 ALSys 测试用例管理系统使用说明，主要面向开发和测试同学，核心覆盖环境部署、模块说明、使用示例三大块。重点讲后端实现和使用细节，前端启动、联调的关键信息也会同步，方便大家快速上手。
1. 项目简介
ALSys 是我开发的一套测试用例管理系统，核心目标是简化测试用例的管理、生成和维护流程，适配敏捷开发节奏，主要具备以下功能：
- 项目管理：支持项目的创建、修改、删除和详情查看，实现多项目隔离管理
- 用例管理：可新建、编辑、删除测试用例，支持按目录组织用例，条理更清晰
- 用例上传：支持 XMind 脑图上传，自动解析内容并落库，无需手动录入用例
- AI 生成用例：结合 Coze 平台，通过工作流生成初始用例，再用 Agent 流式修订，提升用例生成效率
- 系统日志：记录所有关键接口的操作审计信息，方便问题追溯和权限审计
2. 技术栈与目录结构
2.1 技术栈
前后端技术栈均采用主流方案，兼顾稳定性和开发效率，具体如下：
- 后端：Spring Boot 2.7.18（核心框架）、Spring Security（权限控制）、MyBatis-Plus（ORM 框架）、MySQL 8.x（数据库）、JWT（身份认证）、AOP（日志切面）
- 前端：Vue 3 + Vite（构建工具）、TypeScript（类型校验）、Element Plus（UI 组件）、Pinia（状态管理）、Axios（接口请求）
- 三方服务：阿里云 OSS（文件存储）、Coze（提供 AI 生成和修订能力）
2.2 目录结构
项目整体分为后端和前端两个模块，目录结构清晰，便于维护，具体如下（简化版，重点标注核心目录）：
CaseCreate/
├─ backend/                 # Spring Boot 后端核心目录
│  ├─ src/main/java/        # 核心代码：Controller（接口）、Service（业务逻辑）、Mapper（数据库操作）、Entity（实体类）
│  └─ src/main/resources/   # 配置文件：application.yml（核心配置）、schema.sql、db/*.sql（数据库初始化脚本）
├─ frontend/                # Vue3 前端目录
│  ├─ src/views/            # 页面组件（对应前端各个功能页面）
│  ├─ src/api/              # 接口请求封装（统一管理前端调用的后端接口）
│  └─ src/router/           # 路由配置（前端页面跳转管理）
└─ Readme.md                # 项目说明文档（本文档）
3. 环境部署
部署前请确保满足运行环境要求，步骤按后端、前端依次进行，数据库会自动初始化，无需额外手动操作。
3.1 运行环境要求
以下是实测可用的环境版本，建议按此配置，避免兼容性问题：
- JDK：17（建议版本，1.8 可能存在依赖兼容问题）
- Maven：3.8+（用于后端项目构建）
- Node.js：18+（建议 18 或 20 版本，用于前端依赖安装和启动）
- MySQL：8.x（数据库，低版本可能不支持部分语法）
3.2 数据库准备
只需新建数据库，项目启动时会自动执行初始化脚本，无需手动执行 sql：
1. 新建数据库（示例名称，可自行修改），执行以下 sql：
        CREATE DATABASE case_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
2. 应用启动时，会自动执行 classpath:db/init.sql 脚本，自动创建 7 张核心表，分别是：ai_chat_session、sys_operation_log、sys_user、test_case、test_folder、test_project、test_project_user。
这里简单说明下各表的核心作用（无需记全字段，了解核心用途即可）：
- ai_chat_session：存储 AI 会话相关信息，核心字段是会话 id、会话历史 json、工作流快照，用于追溯 AI 生成和修订的上下文。
- sys_operation_log：系统操作日志表，记录所有关键操作的审计信息，字段简单，后续会优化自动记录逻辑。
- sys_user：系统用户表，存储用户基本信息，用于身份认证和权限控制。
- test_case：测试用例核心表，存储用例编号、类型、优先级、所属目录、XMind 内容等关键信息。
- test_folder：用例目录表，用于组织用例，实现用例的分级管理。
- test_project、test_project_user：两个表配合实现项目与用户的关联，便于按项目分配权限，拆分是为了后续拓展。
3.3 后端配置
后端核心配置文件路径：backend/src/main/resources/application.yml，重点配置以下几项，其余可保持默认：
- 数据库连接：spring.datasource.*，配置数据库地址、用户名、密码
- JWT 配置：jwt.secret，用于生成和验证 JWT 令牌，建议设置复杂密钥
- Coze 配置：alsystem.ai.*，配置 Coze 的 API 密钥等信息，用于 AI 生成用例
- OSS 配置：alsystem.oss.*，配置阿里云 OSS 相关信息，用于文件存储
注意：敏感信息（如密钥、密码）建议用环境变量注入，避免写死在配置文件中，示例如下（可直接复制使用，替换为自己的信息）：
JWT_SECRET=your_jwt_secret
COZI_API_KEY=your_ai_api_key
ALIYUN_ACCESSKEY_ID=xxx
ALIYUN_ACCESSKEY_SECRET=xxx
ALIYUN_OSS_ENDPOINT=xxx
ALIYUN_OSS_BUCKET_NAME=xxx
DATABASE_PASSWORD=xxx
3.4 启动后端
进入后端目录 backend/，执行以下命令启动，默认端口 8080，启动成功后可通过接口工具测试接口：
mvn spring-boot:run
3.5 启动前端
进入前端目录frontend/，先安装依赖，再启动开发环境，默认访问地址：http://localhost:5173：
npm install  # 安装依赖，首次启动需执行
npm run dev  # 启动前端开发环境
前端开发环境的接口基地址配置在 frontend/.env.development 中，若后端端口修改，需同步修改此处的基地址。
4. 模块说明（后端重点）
以下重点讲解后端各核心模块的实现逻辑、核心接口和注意事项，前端联调可参考接口信息，具体页面操作不详细展开。
4.1 鉴权控制
项目采用 Spring Security 实现权限校验，核心逻辑和拦截器、过滤器类似，只是复用了 Spring 封装好的接口，开发更高效。
核心配置类（重点关注，修改权限逻辑需改这几个类）：
- com.alsystem.casemanager.util.SecurityUtil（权限工具类）
- com.alsystem.casemanager.security.JwtAuthenticationFilter（JWT 拦截器）
- com.alsystem.casemanager.security.SecurityConfig（核心安全配置）
4.2 项目管理模块
该模块的核心目的是实现多项目隔离，每个项目有独立的工作空间和用例目录，权限控制基于项目负责人判断。
核心接口（前端联调重点）：
- POST /api/project/create：创建项目
- PUT /api/project/update：修改项目信息
- DELETE /api/project/delete/{projectId}：删除项目（需验证项目负责人权限）
- GET /api/project/list：分页查询项目列表
权限控制逻辑：操作项目时，会判断当前登录用户是否为项目负责人，非负责人无法执行修改、删除等操作，该逻辑通过业务代码判断实现。
核心代码：com.alsystem.casemanager.service.impl.ProjectServiceImpl（项目相关业务逻辑均在此类）
补充说明：项目的独立工作空间，本质是通过数据库中 folder_id、project_id、parent_id 三个字段的关联实现的，前端展示时，先筛选项目 id，再通过目录 id 和父目录 id 构建目录层级。
4.3 用例管理/上传模块
该模块核心是支持用例的手动编辑和 XMind 批量上传，简化用例录入流程，适配敏捷开发的快节奏。
核心接口：
- PUT /api/case/update：编辑单个用例
- POST /api/case/upload/xmind：XMind 脑图上传，上传后会自动解析内容、写入 OSS（辅助存储）、落库到 test_case 表
这里说下为什么选择 XMind 脑图格式存储用例，而不是传统表格：
- 传统表格用例冗余字段多（如用例编号、执行步骤等），增加系统存储负担，也不便于快速编辑
- 敏捷开发节奏快，脑图格式一目了然，能快速呈现用例逻辑，适配快速迭代需求
- 脑图灵活性高，修改、拓展、复用都很方便，无需像表格那样级联修改多个字段
- 脑图结构天然贴合测试思维，用例设计效率更高
核心代码：com.alsystem.casemanager.controller.CaseUploadController（上传相关逻辑暂时放在 Controller 层，后续会重构到 Service 层）
重点说明：XMind 文件的存储方案（踩过的坑分享）
最初考虑过两种存储方式：直接上传到 OSS 或存入数据库。OSS 方式简单，但存在弊端：修改文件需覆盖上传，且取出的文件无法直接给前端解析。因此最终选择将 XMind 内容存入数据库。
XMind 本质是一种特殊的压缩包，解压后包含三个 json 文件：content.json（核心脑图内容）、metadata.json（元信息）、manifest.json（压缩包信息）。其中只有 content.json 有用，另外两个文件内容固定，无需存储。因此，我们只需解压 XMind 文件，提取 content.json 的内容，存入数据库即可，简单高效。
4.4 AI 生成模块（核心模块）
该模块是项目的核心亮点，基于 Coze 平台实现 AI 用例生成和修订，解决手动设计用例效率低的问题。
核心接口（两种生成/修订模式）：
- POST /api/case/ai/chat：工作流模式生成初始用例
- POST /api/case/ai/chat/stream：Agent 流式修订用例（支持实时交互修改）
为什么分成两种模式？核心原因是解决大模型的“幻觉”问题：
如果将生成和修订功能合并，会面临两难选择：提示词复杂则生成慢但准确，提示词简单则生成快但粗糙。且 Coze 工作流有 10 分钟等待上限，复杂生成场景根本不够用。因此拆分两种模式，工作流负责快速生成初始用例，Agent 负责流式修订，兼顾效率和准确性。另外，拆分 Agent 也是为了练习相关技术实现。
关键问题：大模型生成内容与用例存储格式的适配
最初想让大模型直接生成 XMind 文件，但实际测试发现，大模型生成的 XMind 格式易出错，后续修改也困难。因此选择 markdown 作为中间格式——既适合人类阅读修改，也便于大模型解析生成。
格式转换逻辑：大模型生成 markdown 文本 → 解析 markdown 内容 → 用 ObjectNode 构造 json 结构 → 生成 content.json → 补充固定的 metadata.json 和 manifest.json → 压缩为 XMind 文件 → 返回前端触发下载，再由用户手动上传到对应目录。
补充说明：Agent 修订的会话维护
Agent 修订采用流式响应，提升用户体验，底层通过 SSE 协议实现，Spring Boot 中只需将 SseEmitter 作为接口返回对象即可。为了方便追溯修订历史，每个生成/修订的用例下方都有“采用”按钮，点击后会触发上述格式转换和下载流程，用户可自行选择上传到对应项目目录。
4.5 系统日志模块（审计功能）
该模块用于记录所有关键操作，便于审计和问题追溯，核心基于 AOP 切面编程实现（目前是手动埋点，后续会优化为全自动记录）。
核心能力：
- 记录操作人、操作时间、调用接口、接口用途
- 记录操作成功/失败状态，以及失败时的错误信息
- 日志统一落库到 sys_operation_log 表
- 提供日志查询接口：GET /api/system/log/list
- 前端对应页面：左侧菜单“系统日志”
日志记录范围（核心操作全覆盖）：
1. 项目相关操作（新增、修改、删除，关联项目 ID）
2. 用例编辑操作（/api/case/update，关联用例名称）
3. XMind 用例上传（关联用例名称、上传路径、项目 ID）
4. AI 用例生成（工作流和 Agent 两种接口，记录生成状态）
5. 后续优化计划（Todo）
目前项目已实现核心功能，后续会逐步优化完善，重点如下：
- 完善日志管理模块，将手动埋点改为全自动记录，减少开发成本
- 完善用例优先级、用例类型的配置和展示功能
- 拓展 RAG 检索功能，提升 AI 生成用例的准确性（结合项目历史用例）
- 重构用例上传模块，将 Controller 层的业务逻辑迁移到 Service 层，规范代码结构
- 优化前端页面交互，提升用户体验
