# Frontend Bun + Vite Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the CyberMario frontend package workflow from npm to Bun while keeping the existing Vite build chain.

**Architecture:** Bun becomes the package manager and script runner for `fe/`. Vite remains the frontend dev server and
production bundler, so existing React, TypeScript, proxy, and build behavior stay unchanged.

**Tech Stack:** Bun 1.3.14, React 19, TypeScript, Vite, Vitest.

---

## File Structure

- Modify `fe/package.json`: remove direct `@types/node` only if validation succeeds without it.
- Delete `fe/package-lock.json`: remove npm lock state.
- Create `fe/bun.lock`: lock dependency resolution with Bun.
- Modify `fe/README.md`: document Bun commands and validation.
- Modify `README.md`: document Bun as frontend prerequisite and replace npm commands.

---

### Task 1: Generate Bun Lockfile And Remove npm Lock

**Files:**

- Create: `fe/bun.lock`
- Delete: `fe/package-lock.json`

- [ ] **Step 1: Install dependencies with Bun**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun install
```

Expected: `bun.lock` is created or updated, and dependencies install successfully.

- [ ] **Step 2: Remove npm lockfile**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
rm package-lock.json
```

Expected: `package-lock.json` is removed from the working tree.

---

### Task 2: Try Removing Direct Node Type Dependency

**Files:**

- Modify: `fe/package.json`
- Modify: `fe/bun.lock`

- [ ] **Step 1: Remove `@types/node` from devDependencies**

Edit `fe/package.json` and remove this line from `devDependencies`:

```json
"@types/node": "^24.12.3",
```

- [ ] **Step 2: Refresh Bun lockfile**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun install
```

Expected: `bun.lock` reflects the package manifest without direct `@types/node`.

- [ ] **Step 3: Validate whether Node types are still required**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run typecheck
```

Expected if removal is valid: command exits 0. Expected if Node types are required: TypeScript reports missing Node
globals, modules, or type declarations.

- [ ] **Step 4: Restore `@types/node` if required**

If `bun run typecheck` fails because Node types are missing, restore this line in `fe/package.json`:

```json
"@types/node": "^24.12.3",
```

Then run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun install
bun run typecheck
```

Expected: typecheck exits 0 after restoring the dependency.

---

### Task 3: Update Frontend Documentation

**Files:**

- Modify: `fe/README.md`
- Modify: `README.md`

- [ ] **Step 1: Update `fe/README.md` scripts**

Replace npm commands in the Scripts and Validation sections with Bun commands:

```bash
bun run dev
bun run lint
bun run typecheck
bun run test
bun run test:coverage
bun run build
bun run analyze
```

Use this install command:

```bash
bun install
```

- [ ] **Step 2: Update root `README.md` prerequisite and local commands**

Change the frontend prerequisite from Node.js/npm to Bun. Replace frontend local development and validation commands
with:

```bash
cd fe
bun install
```

```bash
cd fe
bun run dev
```

```bash
cd fe
bun run lint
bun run typecheck
bun run test
bun run build
```

---

### Task 4: Validate Migration

**Files:**

- Read: `fe/package.json`
- Read: `fe/bun.lock`
- Read: `README.md`
- Read: `fe/README.md`

- [ ] **Step 1: Run frontend lint**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run lint
```

Expected: command exits 0.

- [ ] **Step 2: Run frontend typecheck**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run typecheck
```

Expected: command exits 0.

- [ ] **Step 3: Run frontend tests**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run test
```

Expected: command exits 0.

- [ ] **Step 4: Run frontend build**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario/fe
bun run build
```

Expected: command exits 0.

- [ ] **Step 5: Check diff hygiene**

Run:

```bash
cd /Users/mario/SelfProject/CyberMario
git diff --check
git status --short
```

Expected: whitespace check exits 0; changed files are limited to Bun lockfile, npm lockfile removal, package manifest if
needed, docs, and this plan/spec documentation.
