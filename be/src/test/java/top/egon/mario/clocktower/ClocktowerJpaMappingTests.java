package top.egon.mario.clocktower;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.clocktower.common.enums.ClocktowerAlignment;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerPhase;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingReason;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerRulingType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleCode;
import top.egon.mario.clocktower.common.enums.ClocktowerRoleType;
import top.egon.mario.clocktower.common.enums.ClocktowerRoomStatus;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.converter.jpa.ClocktowerAlignmentConverter;
import top.egon.mario.clocktower.converter.jpa.ClocktowerNightTypeConverter;
import top.egon.mario.clocktower.converter.jpa.ClocktowerRoleTypeConverter;
import top.egon.mario.clocktower.game.po.ClocktowerGameEventPo;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomProfilePo;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImConversationMemberPo;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.ImMessagePo;
import top.egon.mario.im.po.ImReadStatePo;
import top.egon.mario.room.po.RoomBanPo;
import top.egon.mario.room.po.RoomInvitationPo;
import top.egon.mario.room.po.RoomMemberPo;
import top.egon.mario.room.po.RoomSpacePo;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ClocktowerJpaMappingTests {

    @Autowired
    private EntityManager entityManager;

    @Test
    void roomAndImPoClassesAreManagedByJpaContext() {
        assertManaged(RoomSpacePo.class);
        assertManaged(RoomMemberPo.class);
        assertManaged(RoomInvitationPo.class);
        assertManaged(RoomBanPo.class);
        assertManaged(ImChannelPo.class);
        assertManaged(ImGroupPo.class);
        assertManaged(ImConversationPo.class);
        assertManaged(ImConversationMemberPo.class);
        assertManaged(ImMessagePo.class);
        assertManaged(ImReadStatePo.class);
    }

    @Test
    void clocktowerGamePoClassesAreManagedByJpaContext() {
        assertManaged(ClocktowerRoomProfilePo.class);
        assertManaged(ClocktowerRoomSeatPo.class);
        assertManaged(ClocktowerGamePo.class);
        assertManaged(ClocktowerGameSeatPo.class);
        assertManaged(ClocktowerGameEventPo.class);
    }

    @Test
    void roomReservationActiveFieldsPersistAndReload() {
        RoomMemberPo member = new RoomMemberPo();
        member.setRoomId(801L);
        member.setUserId(901L);
        member.setMemberType("PLAYER");
        member.setStatus("ACTIVE");
        member.setActiveStatus(true);
        member.setSeatNo(1);
        member.setDisplayName("Alice");
        member.setJoinedAt(Instant.parse("2026-01-01T00:00:00Z"));
        entityManager.persist(member);

        RoomInvitationPo invitation = new RoomInvitationPo();
        invitation.setRoomId(801L);
        invitation.setInviterUserId(902L);
        invitation.setInviteeUserId(903L);
        invitation.setInvitationCode("INV-801-1");
        invitation.setStatus("PENDING");
        invitation.setActiveStatus(true);
        invitation.setTargetSeatNo(2);
        entityManager.persist(invitation);

        entityManager.flush();
        entityManager.clear();

        RoomMemberPo reloadedMember = entityManager.find(RoomMemberPo.class, member.getId());
        RoomInvitationPo reloadedInvitation = entityManager.find(RoomInvitationPo.class, invitation.getId());

        assertThat(reloadedMember.getActiveStatus()).isTrue();
        assertThat(reloadedMember.getSeatNo()).isEqualTo(1);
        assertThat(reloadedInvitation.getActiveStatus()).isTrue();
        assertThat(reloadedInvitation.getTargetSeatNo()).isEqualTo(2);
    }

    @Test
    void roomImGameJsonColumnsRoundTripMinimalStrings() {
        ImMessagePo message = new ImMessagePo();
        message.setConversationId(101L);
        message.setSenderMemberId(201L);
        message.setSenderUserId(301L);
        message.setMessageSeq(1L);
        message.setMessageType("TEXT");
        message.setContent("hello");
        message.setPayloadJson("{}");
        message.setMetadataJson("{}");
        message.setSentAt(Instant.parse("2026-01-01T00:00:00Z"));
        entityManager.persist(message);

        ClocktowerGamePo game = new ClocktowerGamePo();
        game.setRoomId(401L);
        game.setGameNo(1);
        game.setScriptCode("TROUBLE_BREWING");
        game.setStatus("RUNNING");
        game.setPhase("DAY");
        game.setBoardSnapshotJson("{}");
        game.setMetadataJson("{}");
        entityManager.persist(game);

        ClocktowerGameEventPo event = new ClocktowerGameEventPo();
        event.setGameId(501L);
        event.setEventSeq(1L);
        event.setEventType("TEST_EVENT");
        event.setPhase("DAY");
        event.setVisibility("PRIVATE");
        event.setVisibleGameSeatIdsJson("[]");
        event.setPayloadJson("{}");
        event.setMetadataJson("{}");
        event.setOccurredAt(Instant.parse("2026-01-01T00:00:00Z"));
        entityManager.persist(event);

        entityManager.flush();
        entityManager.clear();

        ImMessagePo reloadedMessage = entityManager.find(ImMessagePo.class, message.getId());
        ClocktowerGamePo reloadedGame = entityManager.find(ClocktowerGamePo.class, game.getId());
        ClocktowerGameEventPo reloadedEvent = entityManager.find(ClocktowerGameEventPo.class, event.getId());

        assertThat(reloadedMessage.getPayloadJson()).isEqualTo("{}");
        assertThat(reloadedMessage.getMetadataJson()).isEqualTo("{}");
        assertThat(reloadedGame.getBoardSnapshotJson()).isEqualTo("{}");
        assertThat(reloadedGame.getMetadataJson()).isEqualTo("{}");
        assertThat(reloadedEvent.getVisibleGameSeatIdsJson()).isEqualTo("[]");
        assertThat(reloadedEvent.getPayloadJson()).isEqualTo("{}");
        assertThat(reloadedEvent.getMetadataJson()).isEqualTo("{}");
    }

    @Test
    void roomImGameStatusFieldsPersistAndReload() {
        RoomSpacePo room = new RoomSpacePo();
        room.setContextType("CLOCKTOWER");
        room.setContextId(601L);
        room.setRoomCode("ROOM-601");
        room.setName("Room 601");
        room.setVisibility("PUBLIC");
        room.setStatus("OPEN");
        room.setLastActiveAt(Instant.parse("2026-01-01T00:00:00Z"));
        entityManager.persist(room);

        ImConversationPo conversation = new ImConversationPo();
        conversation.setChannelId(701L);
        conversation.setGroupId(702L);
        conversation.setContextType("CLOCKTOWER");
        conversation.setContextId(601L);
        conversation.setScopeType("ROOM");
        conversation.setScopeId(601L);
        conversation.setParticipantKey("ROOM:601");
        conversation.setConversationType("ROOM");
        conversation.setStatus("ACTIVE");
        conversation.setLastActiveAt(Instant.parse("2026-01-01T00:00:00Z"));
        entityManager.persist(conversation);

        ClocktowerGamePo game = new ClocktowerGamePo();
        game.setRoomId(601L);
        game.setGameNo(2);
        game.setScriptCode("TROUBLE_BREWING");
        game.setStatus("FINISHED");
        game.setPhase("ENDED");
        entityManager.persist(game);

        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(RoomSpacePo.class, room.getId()).getStatus()).isEqualTo("OPEN");
        assertThat(entityManager.find(ImConversationPo.class, conversation.getId()).getStatus()).isEqualTo("ACTIVE");
        assertThat(entityManager.find(ClocktowerGamePo.class, game.getId()).getStatus()).isEqualTo("FINISHED");
    }

    @Test
    void clocktowerEnumsExposePhaseOneContracts() {
        assertThat(ClocktowerScriptCode.valueOf("TROUBLE_BREWING")).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(ClocktowerRoomStatus.valueOf("LOBBY")).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(ClocktowerPhase.valueOf("FIRST_NIGHT")).isEqualTo(ClocktowerPhase.FIRST_NIGHT);
    }

    @Test
    void rulingEnumsAndSeatPublicLifeStatusExposePhaseOneContracts() throws Exception {
        assertThat(ClocktowerRulingType.valueOf("MARK_DEAD")).isEqualTo(ClocktowerRulingType.MARK_DEAD);
        assertThat(ClocktowerRulingReason.valueOf("ROLE_ABILITY")).isEqualTo(ClocktowerRulingReason.ROLE_ABILITY);
        assertThat(ClocktowerRulingStatus.valueOf("APPLIED")).isEqualTo(ClocktowerRulingStatus.APPLIED);

        Field publicLifeStatus = ClocktowerSeatPo.class.getDeclaredField("publicLifeStatus");
        assertThat(publicLifeStatus.getAnnotation(Column.class).name()).isEqualTo("public_life_status");

        ClocktowerSeatPo seat = new ClocktowerSeatPo();
        assertThat(seat.getLifeStatus()).isEqualTo("ALIVE");
        assertThat(seat.getPublicLifeStatus()).isEqualTo("ALIVE");
    }

    @Test
    void clocktowerCodedEnumsExposeChineseDescriptions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode roleTypeJson = mapper.valueToTree(ClocktowerRoleType.TOWNSFOLK);
        JsonNode alignmentJson = mapper.valueToTree(ClocktowerAlignment.GOOD);
        JsonNode nightTypeJson = mapper.valueToTree(ClocktowerNightType.FIRST_NIGHT);

        assertThat(roleTypeJson.get("code").asInt()).isEqualTo(1);
        assertThat(roleTypeJson.get("desc").asText()).isEqualTo("镇民");
        assertThat(alignmentJson.get("code").asInt()).isEqualTo(1);
        assertThat(alignmentJson.get("desc").asText()).isEqualTo("善良");
        assertThat(nightTypeJson.get("code").asInt()).isEqualTo(1);
        assertThat(nightTypeJson.get("desc").asText()).isEqualTo("首夜");

        assertThat(mapper.convertValue(1, ClocktowerRoleType.class)).isEqualTo(ClocktowerRoleType.TOWNSFOLK);
        assertThat(mapper.convertValue("邪恶", ClocktowerAlignment.class)).isEqualTo(ClocktowerAlignment.EVIL);
        assertThat(mapper.convertValue("NEUTRAL", ClocktowerAlignment.class)).isEqualTo(ClocktowerAlignment.NEUTRAL);
        assertThat(mapper.convertValue("OTHER_NIGHT", ClocktowerNightType.class)).isEqualTo(
                ClocktowerNightType.OTHER_NIGHT);
    }

    @Test
    void clocktowerRoleCodesExposeChineseDescriptions() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode roleCodeJson = mapper.valueToTree(ClocktowerRoleCode.CHEF);

        assertThat(roleCodeJson.get("code").asInt()).isEqualTo(1);
        assertThat(roleCodeJson.get("desc").asText()).isEqualTo("厨师");
        assertThat(ClocktowerRoleCode.fromJson("IMP")).isEqualTo(ClocktowerRoleCode.IMP);
        assertThat(ClocktowerRoleCode.fromJson("小恶魔")).isEqualTo(ClocktowerRoleCode.IMP);
        assertThat(ClocktowerRoleCode.values()).hasSize(72);
    }

    @Test
    void clocktowerRulePersistenceFieldsUseCodedEnumConverters() throws Exception {
        Field roleType = ClocktowerRolePo.class.getDeclaredField("roleType");
        Field alignment = ClocktowerRolePo.class.getDeclaredField("alignment");
        Field nightType = ClocktowerNightOrderPo.class.getDeclaredField("nightType");

        assertThat(roleType.getType()).isEqualTo(ClocktowerRoleType.class);
        assertThat(alignment.getType()).isEqualTo(ClocktowerAlignment.class);
        assertThat(nightType.getType()).isEqualTo(ClocktowerNightType.class);
        assertThat(roleType.getAnnotation(Convert.class).converter()).isEqualTo(ClocktowerRoleTypeConverter.class);
        assertThat(alignment.getAnnotation(Convert.class).converter()).isEqualTo(ClocktowerAlignmentConverter.class);
        assertThat(nightType.getAnnotation(Convert.class).converter()).isEqualTo(ClocktowerNightTypeConverter.class);
    }

    @Test
    void roomAndScriptEntitiesUseProjectAuditBaseClass() {
        ClocktowerScriptPo script = new ClocktowerScriptPo();
        script.setScriptCode(ClocktowerScriptCode.TROUBLE_BREWING);
        script.setName("暗流涌动");

        ClocktowerRoomPo room = new ClocktowerRoomPo();
        room.setRoomCode("ABC123");
        room.setStatus(ClocktowerRoomStatus.LOBBY);
        room.setPhase(ClocktowerPhase.LOBBY);

        assertThat(script.getScriptCode()).isEqualTo(ClocktowerScriptCode.TROUBLE_BREWING);
        assertThat(room.getStatus()).isEqualTo(ClocktowerRoomStatus.LOBBY);
        assertThat(room.isDeleted()).isFalse();
    }

    private void assertManaged(Class<?> entityClass) {
        boolean managed = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getJavaType)
                .anyMatch(entityClass::equals);

        assertThat(managed).isTrue();
    }
}
