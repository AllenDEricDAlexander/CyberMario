package top.egon.mario.clocktower.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ClocktowerAuditFilterRequest {

    @Size(max = 50)
    private List<@Valid @Positive Long> roomIds = List.of();

    @Size(max = 50)
    private List<@Valid @Positive Long> gameIds = List.of();

    @Size(max = 50)
    private List<@Valid @Positive Long> conversationIds = List.of();

    @Size(max = 128)
    private String roomName;

    public ClocktowerAuditQuery toQuery() {
        return new ClocktowerAuditQuery(roomIds, gameIds, conversationIds, roomName);
    }
}
