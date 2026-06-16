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
import top.egon.mario.agent.memory.po.enums.AgentMemoryEntryType;
import top.egon.mario.agent.memory.po.enums.AgentMemorySessionStatus;

import java.time.Instant;

/**
 * User-owned memory session for Agent and RAG chat entries.
 */
@Getter
@Setter
@Entity
@Table(name = "agent_memory_session")
public class AgentMemorySessionPo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 128)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private AgentMemoryEntryType entryType;

    @Column(name = "title", length = 256)
    private String title;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 128)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentMemorySessionStatus status = AgentMemorySessionStatus.ACTIVE;

    @Column(name = "memory_enabled", nullable = false)
    private boolean memoryEnabled = true;

    @Column(name = "long_term_extraction_enabled", nullable = false)
    private boolean longTermExtractionEnabled = true;

    @Column(name = "short_term_window_turns", nullable = false)
    private int shortTermWindowTurns = 10;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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
