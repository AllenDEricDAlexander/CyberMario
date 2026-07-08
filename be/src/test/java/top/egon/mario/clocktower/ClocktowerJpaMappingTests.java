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
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
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
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameExecutionPo;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameNominationPo;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameVotePo;
import top.egon.mario.clocktower.room.po.ClocktowerRoomPo;
import top.egon.mario.clocktower.room.po.ClocktowerSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.po.ClocktowerRolePo;
import top.egon.mario.clocktower.script.po.ClocktowerScriptPo;
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
    void roomPoClassesAreManagedByJpaContext() {
        assertManaged(RoomSpacePo.class);
        assertManaged(RoomMemberPo.class);
        assertManaged(RoomInvitationPo.class);
        assertManaged(RoomBanPo.class);
    }

    @Test
    void clocktowerGamePoClassesAreManagedByJpaContext() {
        assertManaged(ClocktowerRoomProfilePo.class);
        assertManaged(ClocktowerRoomSeatPo.class);
        assertManaged(ClocktowerGamePo.class);
        assertManaged(ClocktowerGameSeatPo.class);
        assertManaged(ClocktowerGameEventPo.class);
        assertManaged(ClocktowerGameNominationPo.class);
        assertManaged(ClocktowerGameVotePo.class);
        assertManaged(ClocktowerGameExecutionPo.class);
    }

    @Test
    void clocktowerAgentPoClassesAreManagedByJpaContext() {
        assertManaged(ClocktowerActorPo.class);
        assertManaged(ClocktowerAgentProfilePo.class);
        assertManaged(ClocktowerAgentInstancePo.class);
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
    void roomGameJsonColumnsRoundTripMinimalStrings() {
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

        ClocktowerGamePo reloadedGame = entityManager.find(ClocktowerGamePo.class, game.getId());
        ClocktowerGameEventPo reloadedEvent = entityManager.find(ClocktowerGameEventPo.class, event.getId());

        assertThat(reloadedGame.getBoardSnapshotJson()).isEqualTo("{}");
        assertThat(reloadedGame.getMetadataJson()).isEqualTo("{}");
        assertThat(reloadedEvent.getVisibleGameSeatIdsJson()).isEqualTo("[]");
        assertThat(reloadedEvent.getPayloadJson()).isEqualTo("{}");
        assertThat(reloadedEvent.getMetadataJson()).isEqualTo("{}");
    }

    @Test
    void clocktowerGameNominationJsonColumnsRoundTripMinimalStrings() {
        ClocktowerGameNominationPo nomination = new ClocktowerGameNominationPo();
        nomination.setGameId(701L);
        nomination.setDayNo(1);
        nomination.setNominatorGameSeatId(801L);
        nomination.setNomineeGameSeatId(802L);
        nomination.setStatus("OPEN");
        nomination.setRequiredVotes(3);
        nomination.setOpenedAt(Instant.parse("2026-01-01T00:00:00Z"));
        nomination.setMetadataJson("{}");
        entityManager.persist(nomination);

        ClocktowerGameVotePo vote = new ClocktowerGameVotePo();
        vote.setGameId(701L);
        vote.setNominationId(901L);
        vote.setVoterGameSeatId(803L);
        vote.setVoteValue(true);
        vote.setStatus("CAST");
        vote.setMetadataJson("{}");
        entityManager.persist(vote);

        ClocktowerGameExecutionPo execution = new ClocktowerGameExecutionPo();
        execution.setGameId(701L);
        execution.setDayNo(1);
        execution.setStatus("PENDING");
        execution.setMetadataJson("{}");
        entityManager.persist(execution);

        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(ClocktowerGameNominationPo.class, nomination.getId()).getMetadataJson())
                .isEqualTo("{}");
        assertThat(entityManager.find(ClocktowerGameVotePo.class, vote.getId()).getMetadataJson()).isEqualTo("{}");
        assertThat(entityManager.find(ClocktowerGameExecutionPo.class, execution.getId()).getMetadataJson())
                .isEqualTo("{}");
    }

    @Test
    void roomGameStatusFieldsPersistAndReload() {
        RoomSpacePo room = new RoomSpacePo();
        room.setContextType("CLOCKTOWER_ROOM");
        room.setContextId(601L);
        room.setRoomCode("ROOM-601");
        room.setName("Room 601");
        room.setVisibility("PUBLIC");
        room.setStatus("OPEN");
        room.setLastActiveAt(Instant.parse("2026-01-01T00:00:00Z"));
        entityManager.persist(room);

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
