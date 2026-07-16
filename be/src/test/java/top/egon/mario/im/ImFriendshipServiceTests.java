package top.egon.mario.im;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.egon.mario.im.facade.FriendFacade;
import top.egon.mario.im.facade.dto.command.CancelFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.DecideFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.RemoveFriendCommand;
import top.egon.mario.im.facade.dto.command.RequestFriendCommand;
import top.egon.mario.im.facade.dto.command.UpdateFriendRemarkCommand;
import top.egon.mario.im.facade.dto.query.ListFriendRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListFriendsQuery;
import top.egon.mario.im.facade.dto.view.FriendRequestView;
import top.egon.mario.im.facade.dto.view.FriendView;
import top.egon.mario.im.po.ImDmBlockPo;
import top.egon.mario.im.po.enums.ImContactStatus;
import top.egon.mario.im.po.enums.ImGovernanceStatus;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImContactRepository;
import top.egon.mario.im.repository.ImDmBlockRepository;
import top.egon.mario.im.repository.ImFriendshipRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
class ImFriendshipServiceTests {

    private static final AtomicLong UNIQUE = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private FriendFacade friendFacade;

    @Autowired
    private ImFriendshipRepository friendshipRepository;

    @Autowired
    private ImContactRepository contactRepository;

    @Autowired
    private ImDmBlockRepository dmBlockRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        contactRepository.deleteAll();
        friendshipRepository.deleteAll();
        dmBlockRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void requestAcceptRemarkRemoveAndReopenMaintainPairAndDirectedContacts() {
        UserPo mario = user("mario", RbacStatus.ENABLED, false);
        UserPo luigi = user("luigi", RbacStatus.ENABLED, false);

        assertThat(friendFacade.searchUsers(principal(mario), "Luigi", 0, 20).getContent())
                .singleElement()
                .satisfies(user -> {
                    assertThat(user.userId()).isEqualTo(luigi.getId());
                    assertThat(user.displayName()).isEqualTo("Luigi");
                });
        assertThat(friendFacade.searchUsers(principal(mario), "%", 0, 20)).isEmpty();

        FriendRequestView pending = friendFacade.request(new RequestFriendCommand(
                principal(mario), luigi.getId(), "Let's play"));
        FriendRequestView duplicate = friendFacade.request(new RequestFriendCommand(
                principal(mario), luigi.getId(), "ignored duplicate"));

        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(duplicate.id()).isEqualTo(pending.id());
        assertThat(friendFacade.listRequests(new ListFriendRequestsQuery(
                principal(mario), "OUTGOING", 0, 20)).getContent())
                .extracting(FriendRequestView::id)
                .containsExactly(pending.id());
        assertThat(friendFacade.listRequests(new ListFriendRequestsQuery(
                principal(luigi), "INCOMING", 0, 20)).getContent())
                .extracting(FriendRequestView::id)
                .containsExactly(pending.id());

        FriendRequestView accepted = friendFacade.accept(new DecideFriendRequestCommand(
                principal(luigi), pending.id(), "accepted"));

        assertThat(accepted.status()).isEqualTo("ACTIVE");
        assertThat(friendFacade.areActiveFriends(mario.getId(), luigi.getId())).isTrue();
        assertThat(contactRepository.findByFriendshipIdAndDeletedFalse(pending.id()))
                .hasSize(2)
                .allSatisfy(contact -> assertThat(contact.getStatus()).isEqualTo(ImContactStatus.ACTIVE));
        assertThat(friendFacade.listFriends(new ListFriendsQuery(principal(mario), 0, 20)).getContent())
                .singleElement()
                .extracting(FriendView::friendUserId)
                .isEqualTo(luigi.getId());

        FriendView remarked = friendFacade.updateRemark(new UpdateFriendRemarkCommand(
                principal(mario), luigi.getId(), "Brother"));
        assertThat(remarked.remark()).isEqualTo("Brother");
        assertThat(friendFacade.listFriends(new ListFriendsQuery(principal(luigi), 0, 20)).getContent())
                .singleElement()
                .extracting(FriendView::remark)
                .isEqualTo("");

        friendFacade.remove(new RemoveFriendCommand(principal(mario), luigi.getId()));

        assertThat(friendFacade.areActiveFriends(mario.getId(), luigi.getId())).isFalse();
        assertThat(contactRepository.findByFriendshipIdAndDeletedFalse(pending.id()))
                .allSatisfy(contact -> assertThat(contact.getStatus()).isEqualTo(ImContactStatus.REMOVED));

        FriendRequestView reopened = friendFacade.request(new RequestFriendCommand(
                principal(luigi), mario.getId(), "Again"));
        assertThat(reopened.id()).isEqualTo(pending.id());
        assertThat(reopened.status()).isEqualTo("PENDING");
        assertThat(reopened.requesterUserId()).isEqualTo(luigi.getId());
    }

    @Test
    void requestWorkflowEnforcesDirectionStateAndPrincipalRules() {
        UserPo mario = user("mario", RbacStatus.ENABLED, false);
        UserPo luigi = user("luigi", RbacStatus.ENABLED, false);
        FriendRequestView pending = friendFacade.request(new RequestFriendCommand(
                principal(mario), luigi.getId(), ""));

        assertCode(() -> friendFacade.request(new RequestFriendCommand(
                principal(luigi), mario.getId(), "")), "IM_FRIEND_REQUEST_ALREADY_PENDING");
        assertCode(() -> friendFacade.accept(new DecideFriendRequestCommand(
                principal(mario), pending.id(), null)), "IM_FRIEND_REQUEST_DECISION_FORBIDDEN");
        assertCode(() -> friendFacade.cancel(new CancelFriendRequestCommand(
                principal(luigi), pending.id())), "IM_FRIEND_REQUEST_CANCEL_FORBIDDEN");

        FriendRequestView rejected = friendFacade.reject(new DecideFriendRequestCommand(
                principal(luigi), pending.id(), "not now"));
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertCode(() -> friendFacade.cancel(new CancelFriendRequestCommand(
                principal(mario), pending.id())), "IM_FRIEND_REQUEST_NOT_PENDING");
        assertCode(() -> friendFacade.request(new RequestFriendCommand(
                principal(mario), mario.getId(), "")), "IM_FRIEND_SELF_DENIED");
    }

    @Test
    void requestRejectsUnavailableOrBlockedTarget() {
        UserPo mario = user("mario", RbacStatus.ENABLED, false);
        UserPo disabled = user("bowser", RbacStatus.DISABLED, false);
        UserPo luigi = user("luigi", RbacStatus.ENABLED, false);

        assertCode(() -> friendFacade.request(new RequestFriendCommand(
                principal(mario), disabled.getId(), "")), "IM_FRIEND_USER_NOT_FOUND");

        ImDmBlockPo block = new ImDmBlockPo();
        block.setBlockerUserId(luigi.getId());
        block.setBlockedUserId(mario.getId());
        block.setStatus(ImGovernanceStatus.ACTIVE);
        block.setReason("blocked");
        block.setMetadataJson("{}");
        dmBlockRepository.saveAndFlush(block);

        assertCode(() -> friendFacade.request(new RequestFriendCommand(
                principal(mario), luigi.getId(), "")), "IM_FRIEND_REQUEST_BLOCKED");
    }

    private UserPo user(String name, RbacStatus status, boolean locked) {
        long unique = UNIQUE.incrementAndGet();
        UserPo user = new UserPo();
        user.setAccountNo("U" + unique);
        user.setUsername(name + unique);
        user.setNickname(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        user.setPasswordHash("test-hash");
        user.setStatus(status);
        user.setLocked(locked);
        return userRepository.saveAndFlush(user);
    }

    private ImPrincipal principal(UserPo user) {
        return new ImPrincipal(user.getId(), Set.of("IM_USER"), "PLATFORM", Map.of());
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
