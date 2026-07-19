package top.egon.mario.im.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.egon.mario.im.cache.ImSurfaceJoinKeyCache;
import top.egon.mario.im.cache.ImSurfaceJoinKeyRef;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImGroupRepository;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Generates and resolves immutable public join keys for channels and groups.
 */
@Service
public class ImSurfaceJoinKeyService {

    private static final String CHANNEL_PREFIX = "chn_";
    private static final String GROUP_PREFIX = "grp_";
    private static final int RANDOM_BYTES = 16;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final Pattern CHANNEL_KEY = Pattern.compile("^chn_[A-Za-z0-9_-]{22}$");
    private static final Pattern GROUP_KEY = Pattern.compile("^grp_[A-Za-z0-9_-]{22}$");

    private final ImChannelRepository channelRepository;
    private final ImGroupRepository groupRepository;
    private final ImSurfaceJoinKeyCache cache;
    private final SecureRandom secureRandom = new SecureRandom();

    public ImSurfaceJoinKeyService(ImChannelRepository channelRepository,
                                   ImGroupRepository groupRepository,
                                   ImSurfaceJoinKeyCache cache) {
        this.channelRepository = channelRepository;
        this.groupRepository = groupRepository;
        this.cache = cache;
    }

    public String generate(ImSurfaceType surfaceType) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String joinKey = prefix(surfaceType) + randomSuffix();
            if (!exists(surfaceType, joinKey)) {
                return joinKey;
            }
        }
        throw new IllegalStateException("Unable to generate a unique IM surface join key");
    }

    public ImSurfaceJoinKeyRef resolve(String value) {
        String joinKey = requireJoinKey(value);
        ImSurfaceType expectedType = type(joinKey);
        ImSurfaceJoinKeyRef resolved = cache.get(joinKey, () -> load(expectedType, joinKey))
                .orElseThrow(() -> new ImException("IM_SURFACE_NOT_FOUND"));
        if (!expectedType.equals(resolved.surfaceType())) {
            throw new ImException("IM_SURFACE_JOIN_KEY_INVALID");
        }
        return resolved;
    }

    private Optional<ImSurfaceJoinKeyRef> load(ImSurfaceType surfaceType, String joinKey) {
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            return channelRepository.findByJoinKeyAndDeletedFalse(joinKey)
                    .map(channel -> new ImSurfaceJoinKeyRef(ImSurfaceType.CHANNEL, channel.getId()));
        }
        return groupRepository.findByJoinKeyAndDeletedFalse(joinKey)
                .map(group -> new ImSurfaceJoinKeyRef(ImSurfaceType.GROUP, group.getId()));
    }

    private boolean exists(ImSurfaceType surfaceType, String joinKey) {
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            return channelRepository.existsByJoinKey(joinKey);
        }
        if (ImSurfaceType.GROUP.equals(surfaceType)) {
            return groupRepository.existsByJoinKey(joinKey);
        }
        throw new ImException("IM_SURFACE_TYPE_UNSUPPORTED");
    }

    private String prefix(ImSurfaceType surfaceType) {
        if (ImSurfaceType.CHANNEL.equals(surfaceType)) {
            return CHANNEL_PREFIX;
        }
        if (ImSurfaceType.GROUP.equals(surfaceType)) {
            return GROUP_PREFIX;
        }
        throw new ImException("IM_SURFACE_TYPE_UNSUPPORTED");
    }

    private ImSurfaceType type(String joinKey) {
        if (CHANNEL_KEY.matcher(joinKey).matches()) {
            return ImSurfaceType.CHANNEL;
        }
        if (GROUP_KEY.matcher(joinKey).matches()) {
            return ImSurfaceType.GROUP;
        }
        throw new ImException("IM_SURFACE_JOIN_KEY_INVALID");
    }

    private String requireJoinKey(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ImException("IM_SURFACE_JOIN_KEY_REQUIRED");
        }
        return value.trim();
    }

    private String randomSuffix() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
