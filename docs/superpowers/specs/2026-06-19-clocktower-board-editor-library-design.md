# Clocktower Board Editor And Library Design

**日期:** 2026-06-19

**文档类型:** Product / Technical Design

**目标:** 重做 `/clocktower/boards` 的手动配板和已保存配板体验。手动配板不再要求输入角色代码，改为按剧本加载角色树选择；保存配板库支持分页、查询、只看自己的配板，并用校验通过状态决定是否可用于开房间。

**背景:** 当前配板页已有生成候选、手动校验、候选保存和已保存列表，但手动校验仍使用角色代码文本框，用户需要记代码；手动配置不能直接保存；已保存列表没有分页和筛选；后端保存时信任传入的 validation snapshot，不足以保证数量、剧本和角色一致。现有数据库已有 `clocktower_board_config` 和 `clocktower_board_role`，本设计在此基础上补齐编辑器和配板库能力。

---

## 1. 产品口径

- 页面仍使用 `/clocktower/boards`。
- 不引入“草稿 / 已提交”状态。
- 保存永远允许。校验未通过的配板也能保存，作为未通过配板留存在我的配板库。
- 校验通过状态是唯一业务状态: `valid=true` 表示可用于开房间，`valid=false` 表示仅可继续编辑或删除。
- 保存时后端必须重新校验，并保存后端生成的 validation snapshot。
- 开房间只允许使用 `valid=true` 的已保存配板。
- 用户只能查询、编辑回填、删除自己保存的配板。

## 2. 页面结构

页面拆成两个清晰区域。

### 2.1 配板编辑器

编辑器用于生成候选、手动选角色、校验和保存。

主要控件:

- 剧本选择。
- 人数输入。
- 生成参数: 难度、混乱度、邪恶压力、新手友好、候选数、随机种子。
- 手动角色树选择。
- 数量提示: `已选 X / 目标 Y`。
- 操作按钮: 生成配板、手动校验、保存当前配板。

角色树选择使用 Ant Design `TreeSelect`:

- `treeCheckable` 多选。
- 根节点按角色类型分组: 镇民、外来者、爪牙、恶魔、旅行者、传奇。
- 子节点为角色叶子: `角色名 (ROLE_CODE)`。
- 只允许选择叶子角色，分组节点不可作为角色提交。
- 支持搜索中文角色名和角色代码。
- 剧本变化后重新加载该剧本 enabled 角色，并清空不属于新剧本的已选角色。

数量处理:

- 前端展示数量提示和 warning。
- 数量不一致时仍允许保存。
- 点“手动校验”时调用后端 validate，展示后端 issues。
- 后端始终重新判断数量，不信任前端提示。

### 2.2 候选配板

候选表保留当前生成结果能力，并补充编辑回填入口。

字段:

- 候选编号。
- 人数。
- 角色。
- 评分。
- 校验结果。
- 操作。

操作:

- 保存: 调用保存接口，后端重新校验。
- 复制到编辑器: 把候选的 `scriptCode/playerCount/roleCodes` 回填到编辑器，方便人工微调。

### 2.3 我的配板库

配板库是下方已保存配板 table，支持查询和分页。

查询条件:

- 剧本。
- 人数。
- 校验结果: 全部、通过、未通过。

表格字段:

- 编号。
- 剧本。
- 人数。
- 角色。
- 校验结果。
- 保存时间。
- 操作。

操作:

- 编辑: 把历史配板复制到编辑器。
- 删除: 逻辑删除自己的配板。

分页:

- 使用 Ant Design Table pagination。
- 后端返回项目已有 `PageResult` 结构: `records/page/size/total/totalPages`。
- 前端沿用 `usePageData` 风格加载分页数据。

## 3. API 设计

尽量沿用当前 `/api/clocktower/boards`。

### 3.1 Validate Board

`POST /api/clocktower/boards/validate`

Request:

```ts
type ClocktowerBoardValidateRequest = {
  scriptCode: ClocktowerScriptCode
  playerCount: number
  roleCodes: string[]
}
```

Response:

```ts
type BoardValidationResponse = {
  valid: boolean
  typeCounts: ClocktowerRoleTypeCountResponse
  issues: ClocktowerRuleViolationResponse[]
  scores: ClocktowerScoreResponse[]
}
```

新增 Java 层校验:

- `BOARD_ROLE_COUNT_MISMATCH`: 角色数量与人数不一致。
- `BOARD_ROLE_NOT_FOUND`: 角色代码不存在或未启用。
- `BOARD_ROLE_SCRIPT_MISMATCH`: 角色存在但不属于所选剧本。

校验顺序:

1. 规范化 roleCodes: trim、去空值，保留顺序。
2. 查询角色元数据。
3. 生成 Java 层 issues: 数量、角色存在性、剧本归属。
4. 基于属于当前剧本的角色统计角色类型。
5. 调用现有规则引擎校验角色类型结构。
6. 合并 Java issues 和规则引擎 issues；存在 ERROR 即 `valid=false`。

### 3.2 Save Board

`POST /api/clocktower/boards/save`

Request:

```ts
type ClocktowerBoardSaveRequest = {
  scriptCode: ClocktowerScriptCode
  playerCount: number
  difficulty: number
  chaos: number
  evilPressure: number
  newbieFriendly: boolean
  seed?: string | null
  roleCodes: string[]
}
```

Response: `ClocktowerBoardConfigResponse`

规则:

- 后端忽略前端传入的 validation。若为兼容旧前端暂时保留字段，也不能使用它作为落库依据。
- 保存前调用同一套 validate。
- 即使 validation 不通过，也保存配板和 validation snapshot。
- 配板归属写入现有审计创建人字段。
- 返回保存后的 roles summary 和 validation。

### 3.3 List My Boards

`GET /api/clocktower/boards`

Query:

```ts
type ClocktowerBoardQuery = {
  scriptCode?: ClocktowerScriptCode
  playerCount?: number
  valid?: boolean
  page?: number
  size?: number
}
```

Response:

```ts
type PageResult<T> = {
  records: T[]
  page: number
  size: number
  total: number
  totalPages: number
}
```

规则:

- 只返回当前登录用户创建的配板。
- 默认按 `createdAt desc, id desc` 排序。
- 支持按剧本、人数、校验结果过滤。

### 3.4 Delete Board

`DELETE /api/clocktower/boards/{boardId}`

规则:

- 只能删除自己的配板。
- 逻辑删除。
- 不影响未来已引用配板的房间快照。

### 3.5 Room Board Usage

开房间侧只允许选择 `valid=true` 的保存配板。

如果房间创建接口支持 `boardId`:

- 后端按当前用户和 `valid=true` 查询该 board。
- board 不存在、不是自己创建、或 `valid=false` 时拒绝。

如果房间创建接口继续支持直接传 `roleCodes`:

- 后端必须调用 board validate。
- validate 不通过时拒绝创建房间。

## 4. 后端数据设计

已有表:

- `clocktower_board_config`
- `clocktower_board_role`

需要新增一个字段:

```sql
ALTER TABLE clocktower_board_config
  ADD COLUMN valid BOOLEAN NOT NULL DEFAULT FALSE;
```

理由:

- 配板库需要按校验结果分页查询。
- 用 JSON 字段过滤在 JPA Specification 中不够直接。
- `valid` 是 validation snapshot 的索引化摘要，不是新的业务状态。

迁移规则:

- 新增 exactly one Flyway migration。
- 不修改已有迁移。
- 迁移给旧数据默认 `false`；如果需要回填旧 validation JSON，可在实现时评估数据库 JSON 表达式，但不是第一优先。

实体和响应:

- `ClocktowerBoardConfigPo` 增加 `valid`。
- `ClocktowerBoardConfigResponse` 增加 `valid` 和 `createdAt`。
- 保存时 `config.valid = validation.valid()`。
- 列表按 `valid` 过滤。

## 5. 前端数据流

初始加载:

1. 加载剧本列表。
2. 选择默认剧本。
3. 加载默认剧本角色列表。
4. 加载我的配板库第一页。

剧本切换:

1. 加载新剧本角色。
2. 移除不属于新剧本的已选角色。
3. 清空当前 validation 展示。

手动校验:

1. 从 TreeSelect 读取 roleCodes。
2. 调用 validate。
3. 展示 typeCounts、issues、scores。

保存当前配板:

1. 收集当前编辑器参数和 roleCodes。
2. 调用 save。
3. 展示保存成功。
4. 刷新配板库当前查询页。

编辑历史配板:

1. 从配板库行读取 `scriptCode/playerCount/roleCodes`。
2. 回填编辑器。
3. 加载对应剧本角色。
4. 展示为当前可编辑状态，不覆盖原记录。

## 6. 错误处理

前端:

- 角色数量不一致时使用 warning 文案，不阻止保存。
- 后端返回业务错误时显示 `resolveErrorMessage`。
- 角色列表加载失败时保留错误提示，并禁用角色树。

后端:

- validate 接口不因配板不合法抛错，而是返回 `valid=false` 和 issues。
- save 接口同样不因 validation 不通过抛错。
- list/delete 中的越权或找不到资源使用 `ClocktowerException`。
- create room 使用无效 board 时使用明确业务错误，例如 `CLOCKTOWER_BOARD_INVALID` 或 `CLOCKTOWER_BOARD_NOT_FOUND`。

## 7. 测试范围

后端:

- validate 数量不一致返回 `BOARD_ROLE_COUNT_MISMATCH`。
- validate 未知角色返回 `BOARD_ROLE_NOT_FOUND`。
- validate 角色不属于剧本返回 `BOARD_ROLE_SCRIPT_MISMATCH`。
- save 未通过配板成功保存，`valid=false`，validation snapshot 来自后端。
- save 通过配板保存为 `valid=true`。
- list 只返回当前用户自己的配板。
- list 支持剧本、人数、valid、分页过滤。
- delete 只能删除自己的配板。
- room create / board usage 拒绝 `valid=false` 配板。

前端:

- 手动区渲染角色 TreeSelect。
- 角色树按类型分组，角色叶子包含中文名和代码。
- 数量提示正确展示 `已选 X / 目标 Y`。
- 手动校验调用 validate 并展示 issues。
- 保存当前配板调用 save，保存后刷新配板库。
- 候选配板复制到编辑器。
- 配板库查询条件和分页调用正确。
- 历史配板编辑回填 `scriptCode/playerCount/roleCodes`。

## 8. 非目标

- 不做配板原地更新。
- 不做配板版本历史。
- 不做公开分享或管理员查看全部配板。
- 不做复杂拖拽排序。
- 不把未通过配板暴露给开房间使用。
- 不引入新的前端状态管理库。
