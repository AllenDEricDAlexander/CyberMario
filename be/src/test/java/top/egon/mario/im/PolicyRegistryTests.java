package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.DefaultSendPolicy;
import top.egon.mario.im.policy.DefaultVisibilityPolicy;
import top.egon.mario.im.policy.ImAccessContext;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.policy.PolicyRegistry;
import top.egon.mario.im.policy.SendPolicy;
import top.egon.mario.im.policy.VisibilityPolicy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyRegistryTests {

    private static final Instant NOW = Instant.parse("2026-06-27T00:00:00Z");
    private static final ImPrincipal AUTHENTICATED = new ImPrincipal(1L, Set.of(), "IM_TEST", Map.of());

    private final DefaultSendPolicy sendPolicy = new DefaultSendPolicy();
    private final DefaultVisibilityPolicy visibilityPolicy = new DefaultVisibilityPolicy();

    @Test
    void defaultVisibilityAllowsActiveChannelMainForAnonymousOrAuthenticatedCaller() {
        ImAccessContext anonymous = channelMain()
                .principal(null)
                .build();
        ImAccessContext authenticated = channelMain()
                .principal(AUTHENTICATED)
                .build();
        ImAccessContext archivedSurface = channelMain()
                .surfaceStatus(ImSurfaceStatus.ARCHIVED)
                .build();

        assertThat(visibilityPolicy.canRead(anonymous)).isTrue();
        assertThat(visibilityPolicy.canRead(authenticated)).isTrue();
        assertThat(visibilityPolicy.canRead(archivedSurface)).isFalse();
    }

    @Test
    void defaultVisibilityRequiresActiveGroupMembershipAndDmPairParticipation() {
        assertThat(visibilityPolicy.canRead(group().build())).isTrue();
        assertThat(visibilityPolicy.canRead(group()
                .membershipStatus(ImMembershipStatus.PENDING)
                .build())).isFalse();
        assertThat(visibilityPolicy.canRead(group()
                .membershipStatus(ImMembershipStatus.LEFT)
                .build())).isFalse();
        assertThat(visibilityPolicy.canRead(group()
                .membershipStatus(ImMembershipStatus.BANNED)
                .build())).isFalse();

        assertThat(visibilityPolicy.canRead(dm()
                .dmPairParticipant(true)
                .build())).isTrue();
        assertThat(visibilityPolicy.canRead(dm()
                .dmPairParticipant(false)
                .build())).isFalse();
    }

    @Test
    void defaultSendRequiresActiveChannelOrGroupMemberWithoutMuteOrBan() {
        assertThat(sendPolicy.canSend(channelMain().build())).isTrue();
        assertThat(sendPolicy.canSend(group().build())).isTrue();

        assertThat(sendPolicy.canSend(channelMain()
                .principal(null)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .membershipStatus(ImMembershipStatus.PENDING)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .membershipStatus(ImMembershipStatus.LEFT)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .membershipStatus(ImMembershipStatus.BANNED)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .memberMutedUntil(NOW.plusSeconds(60))
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .conversationMemberMuted(true)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .activeBan(true)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .activeGlobalMute(true)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(channelMain()
                .conversationStatus(ImConversationStatus.ARCHIVED)
                .build())).isFalse();
    }

    @Test
    void defaultSendRequiresUnfrozenDmPairParticipant() {
        assertThat(sendPolicy.canSend(dm()
                .dmPairParticipant(true)
                .dmPairFrozen(false)
                .build())).isTrue();
        assertThat(sendPolicy.canSend(dm()
                .principal(null)
                .dmPairParticipant(true)
                .dmPairFrozen(false)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(dm()
                .dmPairParticipant(false)
                .dmPairFrozen(false)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(dm()
                .dmPairParticipant(true)
                .dmPairFrozen(true)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(dm()
                .dmPairParticipant(true)
                .activeGlobalMute(true)
                .build())).isFalse();
        assertThat(sendPolicy.canSend(dm()
                .dmPairParticipant(true)
                .conversationStatus(ImConversationStatus.ARCHIVED)
                .build())).isFalse();
    }

    @Test
    void registryResolvesExactContextPolicyAndFallsBackToDefaultForUnknownContext() {
        ClocktowerSendPolicy clocktowerSendPolicy = new ClocktowerSendPolicy();
        ClocktowerVisibilityPolicy clocktowerVisibilityPolicy = new ClocktowerVisibilityPolicy();
        PolicyRegistry registry = new PolicyRegistry(
                List.of(sendPolicy, clocktowerSendPolicy),
                List.of(visibilityPolicy, clocktowerVisibilityPolicy));

        assertThat(registry.resolveSendPolicy("CLOCKTOWER_ROOM")).isSameAs(clocktowerSendPolicy);
        assertThat(registry.resolveVisibilityPolicy("CLOCKTOWER_ROOM")).isSameAs(clocktowerVisibilityPolicy);
        assertThat(registry.resolveSendPolicy("UNKNOWN")).isSameAs(sendPolicy);
        assertThat(registry.resolveVisibilityPolicy("UNKNOWN")).isSameAs(visibilityPolicy);
        assertThat(registry.resolve("CLOCKTOWER_ROOM", SendPolicy.class)).isSameAs(clocktowerSendPolicy);
        assertThat(registry.resolve("CLOCKTOWER_ROOM", VisibilityPolicy.class)).isSameAs(clocktowerVisibilityPolicy);
    }

    @Test
    void registryRejectsDuplicateNonDefaultContextPolicyOwners() {
        assertThatThrownBy(() -> new PolicyRegistry(
                List.of(sendPolicy, new ClocktowerSendPolicy(), new DuplicateClocktowerSendPolicy()),
                List.of(visibilityPolicy)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOCKTOWER_ROOM");

        assertThatThrownBy(() -> new PolicyRegistry(
                List.of(sendPolicy),
                List.of(visibilityPolicy, new ClocktowerVisibilityPolicy(), new DuplicateClocktowerVisibilityPolicy())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOCKTOWER_ROOM");
    }

    private static Builder channelMain() {
        return new Builder()
                .conversationType(ImConversationType.CHANNEL_MAIN)
                .surfaceType(ImSurfaceType.CHANNEL)
                .membershipStatus(ImMembershipStatus.ACTIVE);
    }

    private static Builder group() {
        return new Builder()
                .conversationType(ImConversationType.GROUP)
                .surfaceType(ImSurfaceType.GROUP)
                .membershipStatus(ImMembershipStatus.ACTIVE);
    }

    private static Builder dm() {
        return new Builder()
                .conversationType(ImConversationType.DM)
                .surfaceType(ImSurfaceType.DM_PAIR)
                .membershipStatus(null);
    }

    private static final class Builder {

        private ImPrincipal principal = AUTHENTICATED;
        private String contextType = "IM_TEST";
        private Long contextId = 10L;
        private ImConversationType conversationType = ImConversationType.CHANNEL_MAIN;
        private ImConversationStatus conversationStatus = ImConversationStatus.ACTIVE;
        private ImSurfaceType surfaceType = ImSurfaceType.CHANNEL;
        private Long surfaceId = 20L;
        private ImSurfaceStatus surfaceStatus = ImSurfaceStatus.ACTIVE;
        private ImMembershipStatus membershipStatus = ImMembershipStatus.ACTIVE;
        private Instant memberMutedUntil;
        private boolean conversationMemberMuted;
        private boolean activeBan;
        private boolean activeGlobalMute;
        private boolean dmPairParticipant;
        private boolean dmPairFrozen;
        private Instant now = NOW;

        Builder principal(ImPrincipal principal) {
            this.principal = principal;
            return this;
        }

        Builder conversationType(ImConversationType conversationType) {
            this.conversationType = conversationType;
            return this;
        }

        Builder conversationStatus(ImConversationStatus conversationStatus) {
            this.conversationStatus = conversationStatus;
            return this;
        }

        Builder surfaceType(ImSurfaceType surfaceType) {
            this.surfaceType = surfaceType;
            return this;
        }

        Builder surfaceStatus(ImSurfaceStatus surfaceStatus) {
            this.surfaceStatus = surfaceStatus;
            return this;
        }

        Builder membershipStatus(ImMembershipStatus membershipStatus) {
            this.membershipStatus = membershipStatus;
            return this;
        }

        Builder memberMutedUntil(Instant memberMutedUntil) {
            this.memberMutedUntil = memberMutedUntil;
            return this;
        }

        Builder conversationMemberMuted(boolean conversationMemberMuted) {
            this.conversationMemberMuted = conversationMemberMuted;
            return this;
        }

        Builder activeBan(boolean activeBan) {
            this.activeBan = activeBan;
            return this;
        }

        Builder activeGlobalMute(boolean activeGlobalMute) {
            this.activeGlobalMute = activeGlobalMute;
            return this;
        }

        Builder dmPairParticipant(boolean dmPairParticipant) {
            this.dmPairParticipant = dmPairParticipant;
            return this;
        }

        Builder dmPairFrozen(boolean dmPairFrozen) {
            this.dmPairFrozen = dmPairFrozen;
            return this;
        }

        ImAccessContext build() {
            return new ImAccessContext(principal, contextType, contextId, conversationType, conversationStatus,
                    surfaceType, surfaceId, surfaceStatus, membershipStatus, memberMutedUntil,
                    conversationMemberMuted, activeBan, activeGlobalMute, dmPairParticipant, dmPairFrozen, now);
        }
    }

    private static class ClocktowerSendPolicy implements SendPolicy {

        @Override
        public String contextType() {
            return "CLOCKTOWER_ROOM";
        }

        @Override
        public boolean canSend(ImAccessContext context) {
            return true;
        }
    }

    private static class DuplicateClocktowerSendPolicy extends ClocktowerSendPolicy {
    }

    private static class ClocktowerVisibilityPolicy implements VisibilityPolicy {

        @Override
        public String contextType() {
            return "CLOCKTOWER_ROOM";
        }

        @Override
        public boolean canRead(ImAccessContext context) {
            return true;
        }
    }

    private static class DuplicateClocktowerVisibilityPolicy extends ClocktowerVisibilityPolicy {
    }
}
