package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.ApproveCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.JoinCommand;
import top.egon.mario.im.facade.dto.command.RemoveMemberCommand;
import top.egon.mario.im.facade.dto.query.ListJoinRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListSurfaceMembersQuery;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.JoinRequestView;
import top.egon.mario.im.facade.dto.view.JoinResultView;
import top.egon.mario.im.facade.dto.view.SurfaceMemberView;
import top.egon.mario.im.po.ImMembershipPo;
import top.egon.mario.im.po.enums.ImMembershipRole;
import top.egon.mario.im.po.enums.ImMembershipStatus;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImConversationMemberRepository;
import top.egon.mario.im.repository.ImGroupRepository;
import top.egon.mario.im.repository.ImMembershipRepository;
import top.egon.mario.im.service.ImException;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.po.enums.RbacStatus;
import top.egon.mario.rbac.repository.UserRepository;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.ai.dashscope.api-key=test-api-key")
@Transactional
class ImSurfaceAdministrationTests {

    @Autowired
    private RoomFacade roomFacade;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ImMembershipRepository membershipRepository;

    @Autowired
    private ImConversationMemberRepository conversationMemberRepository;

    @Autowired
    private ImGroupRepository groupRepository;

    @Test
    void ownerAndAdminCanReviewRequestsListMembersAndRemoveOrdinaryMembers() {
        UserPo owner = user("surface-owner", "Surface Owner");
        UserPo admin = user("surface-admin", "Surface Admin");
        UserPo member = user("surface-member", "Surface Member");
        UserPo applicant = user("surface-applicant", "Surface Applicant");
        GroupView group = group(owner.getId(), "surface-admin-" + owner.getId(), "Surface Admin", "APPROVAL");

        JoinResultView adminRequest = roomFacade.applyJoin(new JoinCommand(
                principal(admin.getId()), "GROUP", group.id(), "admin"));
        roomFacade.approveJoin(new ApproveCommand(principal(owner.getId()), adminRequest.joinRequestId()));
        ImMembershipPo adminMembership = membershipRepository
                .findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                        ImSurfaceType.GROUP, group.id(), admin.getId())
                .orElseThrow();
        adminMembership.setMemberRole(ImMembershipRole.ADMIN);
        membershipRepository.saveAndFlush(adminMembership);

        JoinResultView memberRequest = roomFacade.applyJoin(new JoinCommand(
                principal(member.getId()), "GROUP", group.id(), "member"));
        roomFacade.approveJoin(new ApproveCommand(principal(admin.getId()), memberRequest.joinRequestId()));
        JoinResultView pending = roomFacade.applyJoin(new JoinCommand(
                principal(applicant.getId()), "GROUP", group.id(), "pending"));

        Page<JoinRequestView> requests = roomFacade.listJoinRequests(new ListJoinRequestsQuery(
                principal(admin.getId()), "GROUP", group.id(), 0, 20));
        assertThat(requests.getContent())
                .extracting(JoinRequestView::joinRequestId)
                .containsExactly(pending.joinRequestId());
        assertThat(requests.getContent().getFirst())
                .extracting(JoinRequestView::accountNo, JoinRequestView::displayName, JoinRequestView::available)
                .containsExactly("surface-applicant", "Surface Applicant", true);

        Page<SurfaceMemberView> members = roomFacade.listMembers(new ListSurfaceMembersQuery(
                principal(owner.getId()), "GROUP", group.id(), 0, 20));
        assertThat(members.getContent())
                .extracting(SurfaceMemberView::userId)
                .containsExactly(owner.getId(), admin.getId(), member.getId());

        roomFacade.removeMember(new RemoveMemberCommand(
                principal(admin.getId()), "GROUP", group.id(), member.getId()));

        assertThat(membershipRepository.findBySurfaceTypeAndSurfaceIdAndUserIdAndDeletedFalse(
                ImSurfaceType.GROUP, group.id(), member.getId())).get()
                .extracting(ImMembershipPo::getStatus)
                .isEqualTo(ImMembershipStatus.LEFT);
        assertThat(conversationMemberRepository.findByConversationIdAndUserIdAndDeletedFalse(
                group.conversationId(), member.getId())).get()
                .extracting(row -> row.getStatus())
                .isEqualTo(ImMembershipStatus.LEFT);
        assertThat(groupRepository.findById(group.id()).orElseThrow().getMemberCount()).isEqualTo(2);
    }

    @Test
    void ordinaryMemberCannotUseSurfaceAdministrationOrRemovePrivilegedMembers() {
        UserPo owner = user("surface-deny-owner", "Deny Owner");
        UserPo member = user("surface-deny-member", "Deny Member");
        GroupView group = group(owner.getId(), "surface-deny-" + owner.getId(), "Surface Deny", "OPEN");
        roomFacade.applyJoin(new JoinCommand(principal(member.getId()), "GROUP", group.id(), null));

        assertThatThrownBy(() -> roomFacade.listMembers(new ListSurfaceMembersQuery(
                principal(member.getId()), "GROUP", group.id(), 0, 20)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SURFACE_MANAGEMENT_REQUIRED");
        assertThatThrownBy(() -> roomFacade.listJoinRequests(new ListJoinRequestsQuery(
                principal(member.getId()), "GROUP", group.id(), 0, 20)))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SURFACE_MANAGEMENT_REQUIRED");
        assertThatThrownBy(() -> roomFacade.removeMember(new RemoveMemberCommand(
                principal(member.getId()), "GROUP", group.id(), owner.getId())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_SURFACE_MANAGEMENT_REQUIRED");
        assertThatThrownBy(() -> roomFacade.removeMember(new RemoveMemberCommand(
                principal(owner.getId()), "GROUP", group.id(), owner.getId())))
                .isInstanceOf(ImException.class)
                .extracting("code")
                .isEqualTo("IM_MEMBER_SELF_REMOVE_DENIED");
    }

    private UserPo user(String accountNo, String displayName) {
        UserPo user = new UserPo();
        user.setAccountNo(accountNo);
        user.setUsername(accountNo);
        user.setNickname(displayName);
        user.setPasswordHash("test-password-hash");
        user.setStatus(RbacStatus.ENABLED);
        return userRepository.saveAndFlush(user);
    }

    private GroupView group(Long ownerUserId, String groupKey, String name, String joinPolicy) {
        return roomFacade.createGroup(new CreateGroupCommand(
                principal(ownerUserId), null, "IM_SURFACE_ADMIN_TEST", null,
                groupKey, name, joinPolicy, "{}"));
    }

    private ImPrincipal principal(Long userId) {
        return new ImPrincipal(userId, Set.of(), "IM_SURFACE_ADMIN_TEST", Map.of());
    }
}
