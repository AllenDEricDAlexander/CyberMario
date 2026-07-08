package top.egon.mario.clocktower.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import top.egon.mario.clocktower.agent.constant.ClocktowerActorType;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentAutoMode;
import top.egon.mario.clocktower.agent.constant.ClocktowerAgentStatus;
import top.egon.mario.clocktower.agent.dto.ClocktowerAgentSeatDescriptor;
import top.egon.mario.clocktower.agent.po.ClocktowerActorPo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentInstancePo;
import top.egon.mario.clocktower.agent.po.ClocktowerAgentProfilePo;
import top.egon.mario.clocktower.agent.repository.ClocktowerActorRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentInstanceRepository;
import top.egon.mario.clocktower.agent.repository.ClocktowerAgentProfileRepository;
import top.egon.mario.clocktower.agent.service.ClocktowerAgentSeatService;
import top.egon.mario.clocktower.common.ClocktowerException;
import top.egon.mario.clocktower.game.po.ClocktowerRoomSeatPo;
import top.egon.mario.clocktower.room.repository.ClocktowerRoomSeatRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClocktowerAgentSeatServiceImpl implements ClocktowerAgentSeatService {

    private static final String DEFAULT_PROFILE_NAME = "balanced";
    private static final String DEFAULT_AGENT_POLICY = "HEURISTIC_V0";
    private static final String CREATED_BY_AGENT_SEAT_COUNT = "agentSeatCount";
    private static final String SEAT_STATUS_OCCUPIED = "OCCUPIED";
    private static final Set<String> SUPPORTED_AUTO_MODES = Set.of(
            ClocktowerAgentAutoMode.FULL_AUTO,
            ClocktowerAgentAutoMode.ST_APPROVAL,
            ClocktowerAgentAutoMode.PAUSED
    );

    private final ClocktowerActorRepository actorRepository;
    private final ClocktowerAgentProfileRepository profileRepository;
    private final ClocktowerAgentInstanceRepository instanceRepository;
    private final ClocktowerRoomSeatRepository roomSeatRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClocktowerAgentSeatDescriptor createAgentForRoomSeat(Long roomId, Long roomSeatId, int seatNo,
                                                               String displayName, String roleCode,
                                                               String profileName, String autoMode) {
        if (roomId == null || roomSeatId == null || seatNo <= 0) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_ROOM_SEAT_NOT_FOUND");
        }
        String resolvedProfileName = resolveProfileName(profileName);
        String resolvedAutoMode = resolveAutoMode(autoMode);
        ClocktowerAgentProfilePo profile = profileRepository.findFirstByNameAndDeletedFalse(resolvedProfileName)
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_PROFILE_NOT_FOUND"));
        ClocktowerRoomSeatPo seat = roomSeatRepository.findById(roomSeatId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ClocktowerException("CLOCKTOWER_AGENT_ROOM_SEAT_NOT_FOUND"));
        if (!roomId.equals(seat.getRoomId()) || seatNo != seat.getSeatNo()) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_ROOM_SEAT_MISMATCH");
        }
        if (seat.getAgentInstanceId() != null) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_SEAT_ALREADY_BOUND");
        }

        String resolvedDisplayName = resolveDisplayName(displayName, profile, seatNo);
        Map<String, Object> seatMetadata = agentSeatMetadata(resolvedAutoMode);

        ClocktowerActorPo actor = new ClocktowerActorPo();
        actor.setActorType(ClocktowerActorType.AGENT);
        actor.setDisplayName(resolvedDisplayName);
        actor.setStatus(ClocktowerAgentStatus.ACTIVE);
        actor.setMetadataJson(writeJson(actorMetadata(profile, seatNo)));
        ClocktowerActorPo savedActor = actorRepository.saveAndFlush(actor);

        ClocktowerAgentInstancePo instance = new ClocktowerAgentInstancePo();
        instance.setRoomId(roomId);
        instance.setProfileId(profile.getId());
        instance.setActorId(savedActor.getId());
        instance.setRoomSeatId(roomSeatId);
        instance.setStatus(ClocktowerAgentStatus.ACTIVE);
        instance.setAutoMode(resolvedAutoMode);
        instance.setMetadataJson(writeJson(instanceMetadata(profile, seatNo, resolvedAutoMode)));
        ClocktowerAgentInstancePo savedInstance = instanceRepository.saveAndFlush(instance);

        seat.setActorId(savedActor.getId());
        seat.setActorType(ClocktowerActorType.AGENT);
        seat.setAgentInstanceId(savedInstance.getId());
        seat.setUserId(null);
        seat.setDisplayName(resolvedDisplayName);
        seat.setRoleCode(roleCode);
        seat.setStatus(SEAT_STATUS_OCCUPIED);
        seat.setMetadataJson(writeJson(seatMetadata));
        roomSeatRepository.saveAndFlush(seat);

        return new ClocktowerAgentSeatDescriptor(savedActor.getId(), savedInstance.getId(), roomId, roomSeatId,
                seatNo, resolvedDisplayName, roleCode, profile.getName(), resolvedAutoMode, Map.copyOf(seatMetadata));
    }

    @Override
    public boolean isSystemAgentSeat(String actorType, Long agentInstanceId, Map<String, Object> metadata) {
        if (!ClocktowerActorType.AGENT.equals(actorType) || agentInstanceId == null || metadata == null) {
            return false;
        }
        return Boolean.TRUE.equals(metadata.get("systemManaged"))
                && CREATED_BY_AGENT_SEAT_COUNT.equals(String.valueOf(metadata.get("createdBy")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerAgentInstancePo> agentsOfRoom(Long roomId) {
        if (roomId == null) {
            return List.of();
        }
        return instanceRepository.findByRoomIdAndDeletedFalseOrderByIdAsc(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClocktowerAgentInstancePo> agentsOfGame(Long gameId) {
        if (gameId == null) {
            return List.of();
        }
        return instanceRepository.findByGameIdAndDeletedFalseOrderByIdAsc(gameId);
    }

    private String resolveProfileName(String profileName) {
        return StringUtils.hasText(profileName) ? profileName.trim() : DEFAULT_PROFILE_NAME;
    }

    private String resolveAutoMode(String autoMode) {
        String resolved = StringUtils.hasText(autoMode) ? autoMode.trim() : ClocktowerAgentAutoMode.FULL_AUTO;
        if (!SUPPORTED_AUTO_MODES.contains(resolved)) {
            throw new ClocktowerException("CLOCKTOWER_AGENT_AUTO_MODE_INVALID");
        }
        return resolved;
    }

    private String resolveDisplayName(String displayName, ClocktowerAgentProfilePo profile, int seatNo) {
        if (StringUtils.hasText(displayName)) {
            return displayName.trim();
        }
        if (StringUtils.hasText(profile.getDisplayNameTemplate())) {
            String rendered = profile.getDisplayNameTemplate().replace("{n}", String.valueOf(seatNo));
            if (StringUtils.hasText(rendered)) {
                return rendered;
            }
        }
        return "Agent " + seatNo;
    }

    private Map<String, Object> agentSeatMetadata(String autoMode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ready", true);
        metadata.put("actorType", ClocktowerActorType.AGENT);
        metadata.put("agent", true);
        metadata.put("agentSeat", true);
        metadata.put("systemManaged", true);
        metadata.put("agentPolicy", DEFAULT_AGENT_POLICY);
        metadata.put("autoMode", autoMode);
        metadata.put("createdBy", CREATED_BY_AGENT_SEAT_COUNT);
        return metadata;
    }

    private Map<String, Object> actorMetadata(ClocktowerAgentProfilePo profile, int seatNo) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profileName", profile.getName());
        metadata.put("seatNo", seatNo);
        metadata.put("createdBy", CREATED_BY_AGENT_SEAT_COUNT);
        return metadata;
    }

    private Map<String, Object> instanceMetadata(ClocktowerAgentProfilePo profile, int seatNo, String autoMode) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("profileName", profile.getName());
        metadata.put("seatNo", seatNo);
        metadata.put("agentPolicy", DEFAULT_AGENT_POLICY);
        metadata.put("autoMode", autoMode);
        metadata.put("createdBy", CREATED_BY_AGENT_SEAT_COUNT);
        return metadata;
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ClocktowerException("CLOCKTOWER_METADATA_INVALID");
        }
    }
}
