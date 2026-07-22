package top.egon.mario.agent.memory.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryScopeType;
import top.egon.mario.agent.memory.po.enums.AgentLongTermMemoryStatus;

import java.time.Instant;

/**
 * Current Markdown long-term memory document for one user scope.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_long_term_memory")
public class AgentLongTermMemoryPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private AgentLongTermMemoryScopeType scopeType;

    @Column(name = "memory_space_id", length = 96)
    private String memorySpaceId;

    @Column(name = "scope_key", nullable = false, length = 128)
    private String scopeKey = "__web_private__";

    @Column(name = "content_markdown", nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "content_chars", nullable = false)
    private int contentChars;

    @Column(name = "active_version_id")
    private Long activeVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentLongTermMemoryStatus status = AgentLongTermMemoryStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;
}
