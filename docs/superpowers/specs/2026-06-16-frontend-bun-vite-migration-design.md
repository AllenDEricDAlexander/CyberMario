# Frontend Bun + Vite Migration Design

**日期:** 2026-06-16

**目标:** 将 CyberMario 前端从 npm 包管理迁移到 Bun 包管理，并保留现有 React + TypeScript + Vite 构建链。迁移后前端日常命令使用 `bun run ...`，依赖锁定使用 `bun.lock`。

---

## 1. 当前背景

前端位于 `fe/`，当前技术栈是 React 19、TypeScript、Vite、Ant Design、Vitest。`package.json` 已定义 `dev`、`build`、`lint`、`typecheck`、`test`、`test:coverage`、`analyze` 脚本。`vite.config.ts` 负责 React 插件、5173 开发端口、`/api` 与 `/demo` 代理、bundle analyze 输出。

当前 npm 痕迹主要是 `package-lock.json` 和 README 中的 `npm install` / `npm run ...` 命令。项目没有发现 CI 配置文件。

---

## 2. 迁移边界

本次迁移做:

- 使用 Bun 安装前端依赖并生成 `fe/bun.lock`。
- 删除 `fe/package-lock.json`。
- 将前端文档中的 npm 命令改为 Bun 命令。
- 根 README 的前端前置条件从 Node.js/npm 调整为 Bun。
- 优先尝试移除直接的 `@types/node` devDependency；如果 Vite、Vitest、TypeScript 或测试编译需要它，则恢复该依赖并说明原因。

本次迁移不做:

- 不移除 Vite。
- 不迁移到 Bun bundler。
- 不改 React、TypeScript、Ant Design、路由、请求层或页面代码。
- 不引入 shadcn、TanStack Query、TanStack Table 或 TanStack Router。
- 不启动项目。

---

## 3. 为什么保留 Vite

Bun 在本次迁移中承担包管理器和脚本运行器职责；Vite 继续承担前端开发服务器、React 编译、HMR、后端代理和生产构建职责。迁移后 `bun run dev` 仍执行 `vite`，只是脚本入口从 npm 换成 Bun。

移除 Vite 会要求重建开发代理、React 插件、环境变量读取、生产构建产物和分析流程，风险明显高于本次目标收益。因此 Vite 保留。

---

## 4. Node 相关处理原则

“移除 node”在本次迁移中解释为移除项目直接依赖的 npm 包管理痕迹，而不是脱离整个 Node 生态。Vite、Vitest、TypeScript 和许多前端工具仍以 Node 兼容 API 为运行基础，即使通过 Bun 执行脚本，也可能需要 Node 类型声明。

处理策略:

1. 删除 `package-lock.json`。
2. 将命令和文档改为 Bun。
3. 尝试删除 `@types/node`。
4. 运行 `bun run lint`、`bun run typecheck`、`bun run test`、`bun run build`。
5. 如果验证失败且错误指向 Node 类型缺失，恢复 `@types/node`，将其作为 Vite/Vitest/工具链的类型依赖保留。

---

## 5. 预期文件变化

- `fe/bun.lock`: 新增 Bun 锁文件。
- `fe/package-lock.json`: 删除 npm 锁文件。
- `fe/package.json`: 只在验证允许时删除 `@types/node`；否则保持依赖不变。
- `fe/README.md`: 将前端脚本说明改为 Bun 命令。
- `README.md`: 将前端前置条件和本地开发、验证命令改为 Bun。

---

## 6. 验证

在 `fe/` 目录运行:

```bash
bun run lint
bun run typecheck
bun run test
bun run build
```

在仓库根目录运行:

```bash
git diff --check
```

验证失败时不隐藏失败。若失败来自移除 `@types/node`，恢复该依赖后重新验证。

---

## 7. 风险和回滚

主要风险是 Bun 解析依赖后生成的锁文件与 npm lock 的解析结果存在细微差异。通过 lint、typecheck、test 和 build 覆盖基础回归。

回滚方式简单: 恢复 `fe/package-lock.json`、删除 `fe/bun.lock`、将文档命令改回 npm，并重新执行 npm 安装验证。
