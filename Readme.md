# ALSys 使用说明

> 本文档面向开发/测试同学，覆盖 `环境部署`、`模块说明`、`使用示例`。说明以后端为主，同时提供必要的前端启动与联调信息。

---

## 1. 项目简介

ALSys 是一套测试用例管理系统，主要能力包括：

- 项目管理（创建、修改、删除、详情）
- 用例管理（新建、编辑、删除、目录组织）
- 用例上传（XMind 上传解析并落库）
- AI 生成用例（工作流生成 + Agent 修订）
- 系统日志（记录关键接口操作审计信息）

---

## 2. 技术栈与目录结构

### 2.1 技术栈

- 后端：Spring Boot 2.7.18、Spring Security、MyBatis-Plus、MySQL、JWT、AOP
- 前端：Vue 3、Vite、TypeScript、Element Plus、Pinia、Axios
- 三方服务：阿里云 OSS、Coze（AI 能力）

### 2.2 目录结构

```text
CaseCreate/
├─ backend/                 # Spring Boot 后端
│  ├─ src/main/java/        # Controller/Service/Mapper/Entity
│  └─ src/main/resources/   # application.yml / schema.sql / db/*.sql
├─ frontend/                # Vue3 前端
│  ├─ src/views/            # 页面
│  ├─ src/api/              # 接口请求封装
│  └─ src/router/           # 路由
└─ Readme.md  
```

---   

## 3. 环境部署

## 3.1 运行环境要求

- JDK 17（建议）
- Maven 3.8+
- Node.js 18+（建议 18/20）
- MySQL 8.x

## 3.2 数据库准备

1. 新建数据库（示例）：

```sql
CREATE DATABASE case_manager DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 应用启动时会自动执行：
   - `classpath:db/init.sql`
   
在sql初始化脚本中总共会创建7个表：`ai_chat_session`、`sys_operation_log`、`sys_operation_log`、`test_case`、`test_folder`、`test_project`、`test_project_user`

### ai_chat_session
> 聊天会话表，表中有 11 个字段，这里只挑重要的讲。存储会话历史的基本信息、必要信息
- ai_chat_session：会话id，后续主要通过这个id来查找会话和会话历史
- messages_json：会话历史内容，这里存储的形式是json形式（因为大模型传递上下文时采用的就是json格式）
- workflow_context_json：工作流快照，存在的意义是当点击`采用`按钮时就会将这些内容保存到用例中 && 修改用例时会将初始用例给agent。
- coze_conversation_id：扣子会话id，通过这个来追溯工作流上下文。

### sys_operation_log
> 系统日志表，表中 15 个字段。存储操作日志

表中字段比较简单，就不多赘述了

### sys_user
> 系统用户表，共 8 个字段。存储用户的基本信息

表中字段比较简单，就不多赘述了

### test_case
> 测试用例表，共 15 个字段。存储用例的基本信息
- case_code：用例编号
- case_type：用例类型（后续会更新）
- case_level：用例优先级（后续更新）
- folder_id：父目录id
- mind_map_json：xmind格式的内容
- update_by：最后更新人

### test_folder
> 文件目录表，共 8 个字段

表中字段比较简单，就不多赘述了

### test_project、test_project_user
> 两个表功能相同，考虑到拓展原因才分两个表

表中字段都比较简单，就不赘述了。核心功能就是项目-用户之间的连接

## 3.3 后端配置

配置文件：`backend/src/main/resources/application.yml`

重点配置项：

- 数据库连接：`spring.datasource.*`
- JWT：`jwt.secret`
- Coze：`alsystem.ai.*`
- OSS：`alsystem.oss.*`

建议使用环境变量注入敏感信息，例如：

```bash
JWT_SECRET=your_jwt_secret
COZI_API_KEY=your_ai_api_key
ALIYUN_ACCESSKEY_ID=xxx
ALIYUN_ACCESSKEY_SECRET=xxx
ALIYUN_OSS_ENDPOINT=xxx
ALIYUN_OSS_BUCKET_NAME=xxx
DATABASE_PASSWORD=xxx
```
当然也可以写死在配置文件中，但是这样比较危险
## 3.4 启动后端

在 `backend/` 目录执行：

```bash
mvn spring-boot:run
```

默认端口：`8080`

## 3.5 启动前端

在 `frontend/` 目录执行：

```bash
npm install
npm run dev
```

默认访问：`http://localhost:5173`

前端开发环境接口基地址在：`frontend/.env.development`

---

## 4. 模块说明（后端重点）

## 4.1 鉴权控制

项目采用`Spring Security`进行权限校验，校验规则其实和最简单的拦截器、过滤器类似，只是这里使用了 Spring 封装好了的接口实现罢了

**核心配置**：`com.alsystem.casemanager.util.SecurityUtil`、`com.alsystem.casemanager.security.JwtAuthenticationFilter`、`com.alsystem.casemanager.security.SecurityConfig`

## 4.2 项目管理模块

核心接口（示例）：

- `POST /api/project/create`：创建项目
- `PUT /api/project/update`：修改项目
- `DELETE /api/project/delete/{projectId}`：删除项目
- `GET /api/project/list`：分页查询

设计的初心就是想根据不同的项目去提供不同的工作目录。并且这里会根据项目负责人来决定是否有操作权限，这里的权限控制是通过业务判断来控制的。当需要对某个项目进行曹祖时会**判断当前用户是否在项目负责人中**，如果不在就会禁止操作了

**核心代码**：`com.alsystem.casemanager.service.impl.ProjectServiceImpl`

每一个项目都有自己独立的工作空间，这里只是前端展示的功能，具体持久化到数据库中还是得依赖：`folder_id`、`project_id`、`parent_id` 这三个字段的关系。至于怎么构成的映射也是很简单：  先筛选 project_id 再根据 folder_id、parent_id来确定父子关系即可

## 4.3 用例管理/上传模块

- 用例编辑：`PUT /api/case/update`
- XMind 上传：`POST /api/case/upload/xmind`
  - 上传后会解析内容、写入 OSS、落库 `test_case`

这个模块的设计目的就是让用户能够自主上传用例文件（Xmind），并且选择用例所属项目的结构等
``` markdown
用例文件类型选择**脑图格式**而不是像传统的用例一样使用表格来写的原因是：
1. 传统用例所涵盖的内容太冗余，像什么用例编号、执行步骤、预期结果和实际结果。这些字段都没有存在的必要反而会加大系统负担
2. 现在流行的开发模式以敏捷开发、迭代开发为主，节奏快。而脑图这种一目了然的格式更能贴合需求
3. 脑图具有极高灵活性，易改易扩易复用。不像表格那样改一个要级联修改很多个
4. 结构天然匹配测试思维，设计效率高
```
**核心代码**：`com.alsystem.casemanager.controller.CaseUploadController`核心逻辑暂时存放到Controller层，后续更新。

那么这里就有一个不得不关注的事情了：上传的文件是 xmind，怎么保存呢？（怎么展示就是前端的事情了这里不做考虑）有两种保存方式：1.上传到OSS  2.保存到数据库。毫无疑问上传到 OSS 是最简单的实现方式了；但是这种方式有很大的弊端：后续如果想要修改文件岂不是得**覆盖上传**、取出的文件**不能直接交给前端解析**等。

所以这里与其说是选择不如说是没得选。我们只能将 xmind 的文件内容保存到数据库中；那么迎面而来的又是一个新问题： xmind 是一个**特殊的文件**，怎么给文件内容持久化到数据库中呢？这里经过我的调研发现：xmind确实是一特殊的文件，具体来讲其实是一种**特殊的压缩包**，它的本质就是将三个 json 文件压缩后修改后缀名就能得到可以正常读写的 xmind 文件了。 xmind 文件中的三个 json 文件分别是：`content.json`、`metadata.json`、`manifest.json`   其中脑图内容就存储在`content.json`文件中。其它的两个文件分别存储：元信息、压缩包内容；这些东西是写死的没有意义。

那么想要持久化到数据库中就很明了，我们直接将 xmind 文件进行解压缩就好了。这里我们只需要将`content.josn`的原内容取出即可。那么目标就很明确了：直接拿取`content.josn`中的内容存入数据库即可。很简单粗暴。

## 4.4 AI 生成模块
这个模块就属于是项目核心模块了。
>在该模快中我使用的 AI 工作平台是**扣子**

- `POST /api/case/ai/chat`：工作流模式生成
- `POST /api/case/ai/chat/stream`：Agent 流式修订

---
通过上面两个接口可以看到我讲用例的生成分成了两部分：生成、修改。为什么要这样做？原因就是**模型幻觉**。

如果将生成的工作流加上修改功能的话那么就要面临两种选择：提示词庞大生成慢但是准、提示词精简生成快但是粗。而且扣子工作流会有部分延迟并且等待上限就是十分钟，如果想要生成一个很好的用例十分钟是完全不够；但是前者太慢了后者太粗了。所以就分成了两种模式来工作。

如果将 agent 修改功能加上生成的功能的话还是同上方一样，要么慢准要么快粗，但是其实 agent 如果想要嵌入修改功能还是很简单的，这里是出于*练习*的想法才划分 agent 出来的。

现在又迎来了一个新的问题：我们到底想让大模型帮我们生成怎样的用例？   前面已经说过了存储的用例类型是 xmind 格式那难道我们直接让大模型生成一个 xmind 文件出来吗？这种方式是最理想的方式，大模型直接生成的 xmind 文件我们可以直接将这个文件保存到用例库中，省去了很多不必要的功能步骤。但是大模型真的可以生成一个完全没有问题的 xmind 文件吗？就算可以后续的修改难道也可以按照预期进行吗？很显然**是十分困难的**。正是因为大模型的开发性所以才有生成用例的想法，那也正是因为太开放了导致**机器幻觉**等现象很明显。

既然不能直接生成 xmind 文件，那该怎么办。仔细想想，除了脑图以外还有没有既适合人类阅读又方便大模型阅读的格式呢？有的兄弟有的：**markdown 格式**应运而生。为什么选择 markdown ，它相较于另外的 json 格式来讲更适合于人类阅读修改，也同样适用于大模型的阅读。所以这里选择了 markdown 格式

迎面而来的是一个新问题：大模型生成的是 markdown 文本，而用例存储的是 xmind 脑图，我们怎么将 markdown 转化为 xmind 脑图呢？ 聪明的小伙伴可能已经想到了可以直接将 markdown 格式的文本转化为`content.josn`中的内容然后将另外两个写死，将三个文件压缩到一起就可以了。没错，事实上就是这样简单粗暴的干。但是怎么将 markdown 转为 json 格式呢？这就不得不提到 Java 中特别通用化的对象了`ObjectNode`，我们**通过`ObjectNode`对象和`SpringBoot`的自动序列化对象**的特性，就可以很简单的将 markdown 转为 json 了。这里对 markdown 的解析我们可以逐行读取文件内容去筛选构造

具体操作：解析 markdown -> 构造 ObjectNode 对象 -> 转为 json 文件 -> 写死另外俩个文件 -> 压缩后返回前端触发下载逻辑

---
以上就是生成的用例怎么保存到文件系统中。那么大模型是不可能一次性就将用例生成的很完美的，所以我们肯定还需要和 agent 进行交流修改用例，因此这里就需要使用`会话`功能了。大模型和前端交流有两种方式：流式、阻塞式（具体区别我就不详细介绍了）我们这里采用更亲民的流式响应来做。要想维护流式响应就必须利用`SSE协议`，至于什么是 SSE 直接在网上冲浪就可以知晓的很多了（比我讲的会详细很多）。那么Spring Boot 和前端维持`SSE协议`也很简单————将`SseEmitter`作为返回对象即可。

为了方便回溯：我在每一次 agent/工作流 生成的用例下方都有一个 “采用” 按钮，点击这个按钮就会触发上面说的转化流程和下载流程，再由人工选择用例放到哪个文件夹下上传就可


## 4.5 系统日志模块（审计）

关键能力：

- 记录“谁在什么时间调用了哪个接口，以及接口用途”
- 记录成功/失败状态与错误信息
- 审计日志落库：`sys_operation_log`
- 查询接口：`GET /api/system/log/list`
- 前端页面：左侧菜单 `系统日志`

记录范围包括：

1. 项目修改相关（新增/修改/删除，含项目ID）
2. 编辑项目文件内容（`/api/case/update`，含文件名）
3. 用例上传（含用例名、上传路径、项目ID）
4. AI 生成用例（工作流与 Agent 两种接口）

这里的日志也是使用了最熟悉的`AOP切面编程`，不过这些记录的日志都是人工写死的（有点类似于埋点）。后续我会更新成全自动的记录日志。

---

### todo

- 完善日志管理模块
- 完善用例优先级
- 拓展rag检索\
…………