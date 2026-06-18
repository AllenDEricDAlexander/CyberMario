package top.egon.mario.clocktower.grimoire.po;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.converter.jpa.ClocktowerAlignmentConverter;
import top.egon.mario.clocktower.converter.jpa.ClocktowerRoleTypeConverter;
import top.egon.mario.common.entity.BaseAuditablePo;

@Getter
@Setter
@Entity
@Table(name = "clocktower_grimoire_entry")
public class ClocktowerGrimoireEntryPo extends BaseAuditablePo {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "role_code", nullable = false, length = 64)
    private String roleCode;

    @Convert(converter = ClocktowerRoleTypeConverter.class)
    @Column(name = "role_type", nullable = false)
    private ClocktowerRoleType roleType;

    @Convert(converter = ClocktowerAlignmentConverter.class)
    @Column(name = "alignment", nullable = false)
    private ClocktowerAlignment alignment;

    @Column(name = "token_status", nullable = false, length = 32)
    private String tokenStatus = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reminder_tokens_json", nullable = false, columnDefinition = "jsonb")
    private String reminderTokensJson = "[]";

    @Column(name = "notes")
    private String notes;
}
