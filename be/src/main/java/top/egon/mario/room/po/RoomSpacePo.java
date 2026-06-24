package top.egon.mario.room.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.common.entity.BaseAuditablePo;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "room_space")
public class RoomSpacePo extends BaseAuditablePo {

    @Column(name = "context_type", nullable = false, length = 64)
    private String contextType;

    @Column(name = "context_id", nullable = false)
    private Long contextId;

    @Column(name = "room_code", nullable = false, unique = true, length = 64)
    private String roomCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility = "PRIVATE";

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "current_member_count", nullable = false)
    private int currentMemberCount;

    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private String metadataJson = "{}";
}
