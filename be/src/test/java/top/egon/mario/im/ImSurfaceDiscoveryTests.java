package top.egon.mario.im;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.po.ImChannelPo;
import top.egon.mario.im.po.ImGroupPo;
import top.egon.mario.im.po.enums.ImSurfaceStatus;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImChannelRepository;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImConversationRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImInboxRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.facade.ImException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ImSurfaceDiscoveryTests {

    private static final String CONTEXT_TYPE = "IM_SURFACE_DISCOVERY_TEST";

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ImChannelRepository channelRepository;

    @Autowired
    private ImGroupRepository groupRepository;

    @Autowired
    private ImConversationRepository conversationRepository;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImInboxRepository inboxRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void listChannelsReturnsActivePublicContextChannelsSortedWithCallerMembership() {
        ChannelView older = conversationService.createChannel(new CreateChannelCommand(
                principal(2001L), CONTEXT_TYPE, null, "older", "Older", "OPEN", "{}"));
        ChannelView newer = conversationService.createChannel(new CreateChannelCommand(
                principal(2002L), CONTEXT_TYPE, null, "newer", "Newer", "OPEN", "{}"));
        ChannelView archived = conversationService.createChannel(new CreateChannelCommand(
                principal(2003L), CONTEXT_TYPE, null, "archived", "Archived", "OPEN", "{}"));
        ChannelView deleted = conversationService.createChannel(new CreateChannelCommand(
                principal(2004L), CONTEXT_TYPE, null, "deleted", "Deleted", "OPEN", "{}"));
        conversationService.createChannel(new CreateChannelCommand(
                principal(2005L), CONTEXT_TYPE, 9901L, "scoped", "Scoped", "OPEN", "{}"));

        updateChannel(older.id(), Instant.parse("2026-06-27T01:00:00Z"), ImSurfaceStatus.ACTIVE, false);
        updateChannel(newer.id(), Instant.parse("2026-06-27T02:00:00Z"), ImSurfaceStatus.ACTIVE, false);
        updateChannel(archived.id(), Instant.parse("2026-06-27T03:00:00Z"), ImSurfaceStatus.ARCHIVED, false);
        updateChannel(deleted.id(), Instant.parse("2026-06-27T04:00:00Z"), ImSurfaceStatus.ACTIVE, true);
        flushAndClear();

        Counts before = counts();
        List<ChannelView> channels = conversationService.listChannels(
                new ListChannelsQuery(principal(2002L), CONTEXT_TYPE, null));

        assertThat(channels)
                .extracting(ChannelView::channelKey)
                .containsExactly("newer", "older");
        assertThat(channels.get(0).membershipStatus()).isEqualTo("ACTIVE");
        assertThat(channels.get(0).memberRole()).isEqualTo("OWNER");
        assertThat(channels.get(0).canRead()).isTrue();
        assertThat(channels.get(0).canPost()).isTrue();
        assertThat(channels.get(1).membershipStatus()).isNull();
        assertThat(channels.get(1).memberRole()).isNull();
        assertThat(channels.get(1).canRead()).isTrue();
        assertThat(channels.get(1).canPost()).isFalse();
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void listGroupsReturnsChannelOrStandaloneGroupsSortedWithCallerMembership() {
        ChannelView channel = conversationService.createChannel(new CreateChannelCommand(
                principal(2101L), CONTEXT_TYPE, 9911L, "channel", "Channel", "OPEN", "{}"));
        GroupView older = conversationService.createGroup(new CreateGroupCommand(
                principal(2102L), channel.id(), null, null, "older", "Older", "OPEN", "{}"));
        GroupView newer = conversationService.createGroup(new CreateGroupCommand(
                principal(2103L), channel.id(), null, null, "newer", "Newer", "OPEN", "{}"));
        GroupView archived = conversationService.createGroup(new CreateGroupCommand(
                principal(2104L), channel.id(), null, null, "archived", "Archived", "OPEN", "{}"));
        GroupView deleted = conversationService.createGroup(new CreateGroupCommand(
                principal(2105L), channel.id(), null, null, "deleted", "Deleted", "OPEN", "{}"));
        GroupView standalone = conversationService.createGroup(new CreateGroupCommand(
                principal(2106L), null, CONTEXT_TYPE, null, "standalone", "Standalone", "OPEN", "{}"));

        updateGroup(older.id(), Instant.parse("2026-06-27T01:00:00Z"), ImSurfaceStatus.ACTIVE, false);
        updateGroup(newer.id(), Instant.parse("2026-06-27T02:00:00Z"), ImSurfaceStatus.ACTIVE, false);
        updateGroup(archived.id(), Instant.parse("2026-06-27T03:00:00Z"), ImSurfaceStatus.ARCHIVED, false);
        updateGroup(deleted.id(), Instant.parse("2026-06-27T04:00:00Z"), ImSurfaceStatus.ACTIVE, true);
        updateGroup(standalone.id(), Instant.parse("2026-06-27T05:00:00Z"), ImSurfaceStatus.ACTIVE, false);
        flushAndClear();

        Counts beforeChannelDiscovery = counts();
        List<GroupView> channelGroups = conversationService.listGroups(
                new ListGroupsQuery(principal(2103L), channel.id(), null, null));

        assertThat(channelGroups)
                .extracting(GroupView::groupKey)
                .containsExactly("newer", "older");
        assertThat(channelGroups.get(0).membershipStatus()).isEqualTo("ACTIVE");
        assertThat(channelGroups.get(0).memberRole()).isEqualTo("OWNER");
        assertThat(channelGroups.get(0).canRead()).isTrue();
        assertThat(channelGroups.get(0).canPost()).isTrue();
        assertThat(channelGroups.get(1).membershipStatus()).isNull();
        assertThat(channelGroups.get(1).memberRole()).isNull();
        assertThat(channelGroups.get(1).canRead()).isFalse();
        assertThat(channelGroups.get(1).canPost()).isFalse();
        assertThat(counts()).isEqualTo(beforeChannelDiscovery);

        Counts beforeStandaloneDiscovery = counts();
        List<GroupView> standaloneGroups = conversationService.listGroups(
                new ListGroupsQuery(null, null, CONTEXT_TYPE, null));

        assertThat(standaloneGroups)
                .extracting(GroupView::groupKey)
                .containsExactly("standalone");
        assertThat(standaloneGroups.get(0).membershipStatus()).isNull();
        assertThat(standaloneGroups.get(0).canRead()).isFalse();
        assertThat(standaloneGroups.get(0).canPost()).isFalse();
        assertThat(counts()).isEqualTo(beforeStandaloneDiscovery);
    }

    @Test
    void discoveryValidatesRequiredContextForContextScopedQueries() {
        assertThatThrownBy(() -> conversationService.listChannels(new ListChannelsQuery(null, " ", null)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_CONTEXT_TYPE_REQUIRED");
        assertThatThrownBy(() -> conversationService.listGroups(new ListGroupsQuery(null, null, " ", null)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_CONTEXT_TYPE_REQUIRED");
    }

    private void updateChannel(Long id, Instant lastActiveAt, ImSurfaceStatus status, boolean deleted) {
        ImChannelPo channel = channelRepository.findById(id).orElseThrow();
        channel.setLastActiveAt(lastActiveAt);
        channel.setStatus(status);
        channel.setDeleted(deleted);
        channelRepository.save(channel);
    }

    private void updateGroup(Long id, Instant lastActiveAt, ImSurfaceStatus status, boolean deleted) {
        ImGroupPo group = groupRepository.findById(id).orElseThrow();
        group.setLastActiveAt(lastActiveAt);
        group.setStatus(status);
        group.setDeleted(deleted);
        groupRepository.save(group);
    }

    private Counts counts() {
        return new Counts(
                channelRepository.count(),
                groupRepository.count(),
                conversationRepository.count(),
                membershipRepository.count(),
                conversationMemberRepository.count(),
                inboxRepository.count()
        );
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), CONTEXT_TYPE, Map.of());
    }

    private record Counts(
            long channels,
            long groups,
            long conversations,
            long memberships,
            long conversationMembers,
            long inboxes) {
    }
}
