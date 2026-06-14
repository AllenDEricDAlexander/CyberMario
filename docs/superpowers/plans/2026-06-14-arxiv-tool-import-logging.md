# arXiv Tool Import Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the arXiv agent tool so every searched paper is asynchronously imported into the protected super admin
arXiv knowledge base, with logs visible only to super admins.

**Architecture:** Keep `ArxivTools` as the Spring AI function boundary and move search, full-text reading, import, and
logging into focused services. Preseed and protect the `super-admin-arxiv` RAG knowledge base, then record arXiv import
logs and run PDF import into existing RAG ingestion.

**Tech Stack:** Java 21, Spring Boot, Spring AI tool callbacks, Spring Data JPA, Flyway, Reactor scheduler, existing RAG
ingestion services.

---

### Task 1: Protected Super Admin Knowledge Base

**Files:**

- Create: `be/src/main/java/top/egon/mario/rag/service/bootstrap/SuperAdminArxivKnowledgeBaseBootstrap.java`
- Modify: `be/src/main/java/top/egon/mario/rag/service/impl/RagKnowledgeBaseServiceImpl.java`
- Test: `be/src/test/java/top/egon/mario/rag/service/bootstrap/SuperAdminArxivKnowledgeBaseBootstrapTests.java`
- Test: `be/src/test/java/top/egon/mario/rag/service/RagKnowledgeBaseProtectionTests.java`

- [ ] Write failing tests for first-run creation, idempotent bootstrap, DB deletion rebuild, and protected delete.
- [ ] Implement the bootstrap and delete guard.
- [ ] Run the focused tests.

### Task 2: arXiv DTOs, Properties, and Logs

**Files:**

- Create/modify DTOs under `be/src/main/java/top/egon/mario/agent/tools/arxiv/dto`
- Create: `be/src/main/java/top/egon/mario/agent/tools/arxiv/config/ArxivToolProperties.java`
- Create: `be/src/main/java/top/egon/mario/agent/tools/arxiv/po/ArxivToolLogPo.java`
- Create: `be/src/main/java/top/egon/mario/agent/tools/arxiv/po/enums/ArxivToolLogStatus.java`
- Create: `be/src/main/java/top/egon/mario/agent/tools/arxiv/repository/ArxivToolLogRepository.java`
- Create migration `be/src/main/resources/db/migration/V10__create_arxiv_tool_log.sql`

- [ ] Write failing repository/DTO behavior tests.
- [ ] Implement properties, entity, enum, repository, and migration.
- [ ] Run focused tests.

### Task 3: Search, Full Text, and Async Import

**Files:**

- Modify: `be/src/main/java/top/egon/mario/agent/tools/arxiv/ArxivTools.java`
- Create: `be/src/main/java/top/egon/mario/agent/tools/arxiv/ArxivPaperService.java`
- Create: `be/src/main/java/top/egon/mario/agent/tools/arxiv/ArxivImportService.java`
- Create: `be/src/main/java/top/egon/mario/agent/tools/arxiv/ArxivToolUserContext.java`
- Modify: RAG document service to accept arXiv temp-file imports.

- [ ] Write failing tests showing every search result queues import to `super-admin-arxiv`, non-super users cannot see
  the target KB, full-text preview is bounded, and duplicate `entryId` skips reimport.
- [ ] Implement services and tool wiring.
- [ ] Run focused tests.

### Task 4: Principal Propagation and Super Admin Logs

**Files:**

- Modify: `be/src/main/java/top/egon/mario/web/ChatController.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/ChatAgentService.java`
- Modify: `be/src/main/java/top/egon/mario/agent/service/impl/ReactAgentChatService.java`
- Create: admin arXiv log service/controller and RBAC resource seeds.

- [ ] Write failing tests for principal propagation and super-admin-only log access.
- [ ] Implement controller/service/RBAC changes.
- [ ] Run focused tests.

### Task 5: Verification

- [ ] Run targeted Maven tests for arXiv, RAG bootstrap/protection, agent chat config, and RBAC resources.
- [ ] Run backend compile/test scope appropriate for touched modules.
- [ ] Summarize validation evidence and residual risks.
