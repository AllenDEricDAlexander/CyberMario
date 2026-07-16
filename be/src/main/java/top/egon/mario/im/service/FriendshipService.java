package top.egon.mario.im.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import top.egon.mario.im.facade.dto.command.CancelFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.DecideFriendRequestCommand;
import top.egon.mario.im.facade.dto.command.RemoveFriendCommand;
import top.egon.mario.im.facade.dto.command.RequestFriendCommand;
import top.egon.mario.im.facade.dto.command.UpdateFriendRemarkCommand;
import top.egon.mario.im.facade.dto.query.ListFriendRequestsQuery;
import top.egon.mario.im.facade.dto.query.ListFriendsQuery;
import top.egon.mario.im.facade.dto.view.FriendRequestView;
import top.egon.mario.im.facade.dto.view.FriendView;
import top.egon.mario.im.po.ImContactPo;
import top.egon.mario.im.po.ImFriendshipPo;
import top.egon.mario.im.po.enums.ImContactStatus;
import top.egon.mario.im.po.enums.ImFriendshipStatus;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.repository.ImContactRepository;
import top.egon.mario.im.repository.ImDmBlockRepository;
import top.egon.mario.im.repository.ImFriendshipRepository;
import top.egon.mario.rbac.application.RbacUserDirectoryFacade;
import top.egon.mario.rbac.dto.response.UserDirectoryItemResponse;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class FriendshipService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int MAX_REMARK_LENGTH = 128;
    private static final int RETRY_LIMIT = 3;

    private final ImFriendshipRepository friendshipRepository;
    private final ImContactRepository contactRepository;
    private final ImDmBlockRepository dmBlockRepository;
    private final RbacUserDirectoryFacade userDirectoryFacade;
    private final TransactionOperations retryTransactionOperations;

    public FriendshipService(ImFriendshipRepository friendshipRepository,
                             ImContactRepository contactRepository,
                             ImDmBlockRepository dmBlockRepository,
                             RbacUserDirectoryFacade userDirectoryFacade,
                             PlatformTransactionManager transactionManager) {
        this.friendshipRepository = friendshipRepository;
        this.contactRepository = contactRepository;
        this.dmBlockRepository = dmBlockRepository;
        this.userDirectoryFacade = userDirectoryFacade;
        this.retryTransactionOperations = requiresNew(transactionManager);
    }

    public FriendRequestView request(RequestFriendCommand command) {
        if (command == null) {
            throw new ImException("IM_FRIEND_REQUEST_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long targetUserId = requireUserId(command.targetUserId(), "IM_FRIEND_TARGET_REQUIRED");
        requireDifferentUsers(principal.userId(), targetUserId);
        requireAvailableUser(targetUserId);
        PairUsers users = pairUsers(principal.userId(), targetUserId);
        if (dmBlockRepository.countActiveBetween(users.userLoId(), users.userHiId()) > 0) {
            throw new ImException("IM_FRIEND_REQUEST_BLOCKED");
        }
        String message = normalizeText(command.message(), MAX_MESSAGE_LENGTH, "IM_FRIEND_REQUEST_MESSAGE_TOO_LONG");
        return retryOnUniqueConflict(() -> requestInTransaction(principal.userId(), targetUserId, users, message));
    }

    @Transactional
    public FriendRequestView accept(DecideFriendRequestCommand command) {
        if (command == null) {
            throw new ImException("IM_FRIEND_DECISION_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImFriendshipPo friendship = requirePendingForReceiver(command.friendshipId(), principal.userId());
        requireAvailableUser(friendship.getRequesterUserId());
        requireAvailableUser(principal.userId());
        Instant now = Instant.now();
        friendship.setStatus(ImFriendshipStatus.ACTIVE);
        friendship.setDecidedBy(principal.userId());
        friendship.setDecidedAt(now);
        friendship.setDecisionReason(normalizeText(
                command.reason(), MAX_MESSAGE_LENGTH, "IM_FRIEND_DECISION_REASON_TOO_LONG"));
        friendship.setActivatedAt(now);
        friendship.setRemovedAt(null);
        friendshipRepository.saveAndFlush(friendship);
        activateContacts(friendship);
        return toRequestView(friendship, principal.userId());
    }

    @Transactional
    public FriendRequestView reject(DecideFriendRequestCommand command) {
        if (command == null) {
            throw new ImException("IM_FRIEND_DECISION_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImFriendshipPo friendship = requirePendingForReceiver(command.friendshipId(), principal.userId());
        friendship.setStatus(ImFriendshipStatus.REJECTED);
        friendship.setDecidedBy(principal.userId());
        friendship.setDecidedAt(Instant.now());
        friendship.setDecisionReason(normalizeText(
                command.reason(), MAX_MESSAGE_LENGTH, "IM_FRIEND_DECISION_REASON_TOO_LONG"));
        friendshipRepository.saveAndFlush(friendship);
        return toRequestView(friendship, principal.userId());
    }

    @Transactional
    public FriendRequestView cancel(CancelFriendRequestCommand command) {
        if (command == null) {
            throw new ImException("IM_FRIEND_CANCEL_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        ImFriendshipPo friendship = requireFriendship(command.friendshipId(), true);
        if (!ImFriendshipStatus.PENDING.equals(friendship.getStatus())) {
            throw new ImException("IM_FRIEND_REQUEST_NOT_PENDING");
        }
        if (!principal.userId().equals(friendship.getRequesterUserId())) {
            throw new ImException("IM_FRIEND_REQUEST_CANCEL_FORBIDDEN");
        }
        friendship.setStatus(ImFriendshipStatus.CANCELLED);
        friendship.setDecidedBy(principal.userId());
        friendship.setDecidedAt(Instant.now());
        friendship.setDecisionReason(null);
        friendshipRepository.saveAndFlush(friendship);
        return toRequestView(friendship, principal.userId());
    }

    @Transactional
    public void remove(RemoveFriendCommand command) {
        if (command == null) {
            throw new ImException("IM_FRIEND_REMOVE_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long friendUserId = requireUserId(command.friendUserId(), "IM_FRIEND_TARGET_REQUIRED");
        requireDifferentUsers(principal.userId(), friendUserId);
        PairUsers users = pairUsers(principal.userId(), friendUserId);
        ImFriendshipPo friendship = friendshipRepository.findLockedByUsers(users.userLoId(), users.userHiId())
                .orElseThrow(() -> new ImException("IM_FRIENDSHIP_NOT_FOUND"));
        if (ImFriendshipStatus.REMOVED.equals(friendship.getStatus())) {
            deactivateContacts(friendship.getId());
            return;
        }
        if (!ImFriendshipStatus.ACTIVE.equals(friendship.getStatus())) {
            throw new ImException("IM_FRIENDSHIP_NOT_ACTIVE");
        }
        friendship.setStatus(ImFriendshipStatus.REMOVED);
        friendship.setRemovedAt(Instant.now());
        friendshipRepository.saveAndFlush(friendship);
        deactivateContacts(friendship.getId());
    }

    @Transactional
    public FriendView updateRemark(UpdateFriendRemarkCommand command) {
        if (command == null) {
            throw new ImException("IM_FRIEND_REMARK_COMMAND_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(command.principal());
        Long friendUserId = requireUserId(command.friendUserId(), "IM_FRIEND_TARGET_REQUIRED");
        ImContactPo contact = contactRepository
                .findByOwnerUserIdAndContactUserIdAndDeletedFalse(principal.userId(), friendUserId)
                .filter(row -> ImContactStatus.ACTIVE.equals(row.getStatus()))
                .orElseThrow(() -> new ImException("IM_FRIENDSHIP_NOT_ACTIVE"));
        contact.setRemark(normalizeText(command.remark(), MAX_REMARK_LENGTH, "IM_FRIEND_REMARK_TOO_LONG"));
        contactRepository.saveAndFlush(contact);
        ImFriendshipPo friendship = friendshipRepository.findByIdAndDeletedFalse(contact.getFriendshipId())
                .orElseThrow(() -> new ImException("IM_FRIENDSHIP_NOT_FOUND"));
        return toFriendView(contact, friendship, userDirectoryFacade.findEnabledById(friendUserId).orElse(null));
    }

    @Transactional(readOnly = true)
    public Page<FriendView> listFriends(ListFriendsQuery query) {
        if (query == null) {
            throw new ImException("IM_FRIEND_LIST_QUERY_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(query.principal());
        PageRequest pageable = pageRequest(query.page(), query.size());
        Page<ImContactPo> contacts = contactRepository.findByOwnerUserIdAndStatusAndDeletedFalse(
                principal.userId(), ImContactStatus.ACTIVE, pageable);
        Map<Long, UserDirectoryItemResponse> users = userDirectoryFacade.findEnabledByIds(
                contacts.getContent().stream().map(ImContactPo::getContactUserId).toList());
        Map<Long, ImFriendshipPo> friendships = friendships(
                contacts.getContent().stream().map(ImContactPo::getFriendshipId).toList());
        List<FriendView> views = contacts.getContent().stream()
                .map(contact -> toFriendView(contact, friendships.get(contact.getFriendshipId()),
                        users.get(contact.getContactUserId())))
                .toList();
        return new PageImpl<>(views, pageable, contacts.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<FriendRequestView> listRequests(ListFriendRequestsQuery query) {
        if (query == null) {
            throw new ImException("IM_FRIEND_REQUEST_LIST_QUERY_REQUIRED");
        }
        ImPrincipal principal = requirePrincipal(query.principal());
        PageRequest pageable = pageRequest(query.page(), query.size());
        Page<ImFriendshipPo> requests = switch (normalizeBox(query.box())) {
            case "INCOMING" -> friendshipRepository.findIncomingPending(principal.userId(), pageable);
            case "OUTGOING" -> friendshipRepository.findOutgoingPending(principal.userId(), pageable);
            default -> throw new ImException("IM_FRIEND_REQUEST_BOX_INVALID");
        };
        List<Long> peerIds = requests.getContent().stream()
                .map(request -> peerUserId(request, principal.userId()))
                .toList();
        Map<Long, UserDirectoryItemResponse> users = userDirectoryFacade.findEnabledByIds(peerIds);
        List<FriendRequestView> views = requests.getContent().stream()
                .map(request -> toRequestView(request, principal.userId(), users.get(peerUserId(request, principal.userId()))))
                .toList();
        return new PageImpl<>(views, pageable, requests.getTotalElements());
    }

    @Transactional(readOnly = true)
    public boolean areActiveFriends(Long firstUserId, Long secondUserId) {
        if (firstUserId == null || secondUserId == null || firstUserId.equals(secondUserId)) {
            return false;
        }
        PairUsers users = pairUsers(firstUserId, secondUserId);
        return friendshipRepository.findByUserLoIdAndUserHiIdAndDeletedFalse(users.userLoId(), users.userHiId())
                .filter(friendship -> ImFriendshipStatus.ACTIVE.equals(friendship.getStatus()))
                .isPresent();
    }

    private FriendRequestView requestInTransaction(Long requesterUserId, Long targetUserId,
                                                   PairUsers users, String message) {
        Instant now = Instant.now();
        ImFriendshipPo friendship = friendshipRepository.findLockedByUsers(users.userLoId(), users.userHiId())
                .map(existing -> reopenOrReturn(existing, requesterUserId, message, now))
                .orElseGet(() -> createFriendship(users, requesterUserId, message, now));
        return toRequestView(friendship, requesterUserId,
                userDirectoryFacade.findEnabledById(targetUserId).orElse(null));
    }

    private ImFriendshipPo reopenOrReturn(ImFriendshipPo friendship, Long requesterUserId,
                                          String message, Instant now) {
        if (ImFriendshipStatus.ACTIVE.equals(friendship.getStatus())) {
            throw new ImException("IM_FRIEND_ALREADY_ACTIVE");
        }
        if (ImFriendshipStatus.PENDING.equals(friendship.getStatus())) {
            if (requesterUserId.equals(friendship.getRequesterUserId())) {
                return friendship;
            }
            throw new ImException("IM_FRIEND_REQUEST_ALREADY_PENDING");
        }
        friendship.setRequesterUserId(requesterUserId);
        friendship.setStatus(ImFriendshipStatus.PENDING);
        friendship.setRequestMessage(message);
        friendship.setDecidedBy(null);
        friendship.setDecidedAt(null);
        friendship.setDecisionReason(null);
        friendship.setRequestedAt(now);
        friendship.setActivatedAt(null);
        friendship.setRemovedAt(null);
        friendship.setMetadataJson("{}");
        return friendshipRepository.saveAndFlush(friendship);
    }

    private ImFriendshipPo createFriendship(PairUsers users, Long requesterUserId, String message, Instant now) {
        ImFriendshipPo friendship = new ImFriendshipPo();
        friendship.setUserLoId(users.userLoId());
        friendship.setUserHiId(users.userHiId());
        friendship.setRequesterUserId(requesterUserId);
        friendship.setStatus(ImFriendshipStatus.PENDING);
        friendship.setRequestMessage(message);
        friendship.setRequestedAt(now);
        friendship.setMetadataJson("{}");
        return friendshipRepository.saveAndFlush(friendship);
    }

    private ImFriendshipPo requirePendingForReceiver(Long friendshipId, Long userId) {
        ImFriendshipPo friendship = requireFriendship(friendshipId, true);
        if (!ImFriendshipStatus.PENDING.equals(friendship.getStatus())) {
            throw new ImException("IM_FRIEND_REQUEST_NOT_PENDING");
        }
        Long receiverUserId = recipientUserId(friendship);
        if (!userId.equals(receiverUserId)) {
            throw new ImException("IM_FRIEND_REQUEST_DECISION_FORBIDDEN");
        }
        return friendship;
    }

    private ImFriendshipPo requireFriendship(Long friendshipId, boolean lock) {
        if (friendshipId == null) {
            throw new ImException("IM_FRIENDSHIP_ID_REQUIRED");
        }
        return (lock ? friendshipRepository.findLockedById(friendshipId)
                        : friendshipRepository.findByIdAndDeletedFalse(friendshipId))
                .orElseThrow(() -> new ImException("IM_FRIENDSHIP_NOT_FOUND"));
    }

    private void activateContacts(ImFriendshipPo friendship) {
        activateContact(friendship, friendship.getUserLoId(), friendship.getUserHiId());
        activateContact(friendship, friendship.getUserHiId(), friendship.getUserLoId());
    }

    private void activateContact(ImFriendshipPo friendship, Long ownerUserId, Long contactUserId) {
        ImContactPo contact = contactRepository
                .findByOwnerUserIdAndContactUserIdAndDeletedFalse(ownerUserId, contactUserId)
                .orElseGet(ImContactPo::new);
        contact.setFriendshipId(friendship.getId());
        contact.setOwnerUserId(ownerUserId);
        contact.setContactUserId(contactUserId);
        contact.setRemark(contact.getRemark() == null ? "" : contact.getRemark());
        contact.setStatus(ImContactStatus.ACTIVE);
        contact.setMetadataJson("{}");
        contactRepository.saveAndFlush(contact);
    }

    private void deactivateContacts(Long friendshipId) {
        List<ImContactPo> contacts = contactRepository.findByFriendshipIdAndDeletedFalse(friendshipId);
        contacts.forEach(contact -> contact.setStatus(ImContactStatus.REMOVED));
        contactRepository.saveAllAndFlush(contacts);
    }

    private Map<Long, ImFriendshipPo> friendships(Collection<Long> friendshipIds) {
        if (friendshipIds == null || friendshipIds.isEmpty()) {
            return Map.of();
        }
        return friendshipRepository.findAllById(friendshipIds).stream()
                .filter(friendship -> !friendship.isDeleted())
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        ImFriendshipPo::getId, friendship -> friendship));
    }

    private FriendView toFriendView(ImContactPo contact, ImFriendshipPo friendship,
                                    UserDirectoryItemResponse user) {
        return new FriendView(
                contact.getFriendshipId(),
                contact.getContactUserId(),
                user == null ? null : user.accountNo(),
                user == null ? "用户 " + contact.getContactUserId() : user.displayName(),
                user == null ? null : user.avatarUrl(),
                contact.getRemark(),
                user != null,
                friendship == null ? null : friendship.getActivatedAt()
        );
    }

    private FriendRequestView toRequestView(ImFriendshipPo friendship, Long viewerUserId) {
        Long peerUserId = peerUserId(friendship, viewerUserId);
        return toRequestView(friendship, viewerUserId,
                userDirectoryFacade.findEnabledById(peerUserId).orElse(null));
    }

    private FriendRequestView toRequestView(ImFriendshipPo friendship, Long viewerUserId,
                                            UserDirectoryItemResponse peer) {
        Long recipientUserId = recipientUserId(friendship);
        Long peerUserId = peerUserId(friendship, viewerUserId);
        return new FriendRequestView(
                friendship.getId(),
                friendship.getRequesterUserId(),
                recipientUserId,
                peerUserId,
                peer == null ? null : peer.accountNo(),
                peer == null ? "用户 " + peerUserId : peer.displayName(),
                peer == null ? null : peer.avatarUrl(),
                peer != null,
                friendship.getStatus().name(),
                friendship.getRequestMessage(),
                friendship.getRequestedAt(),
                friendship.getDecidedAt(),
                friendship.getDecisionReason()
        );
    }

    private Long recipientUserId(ImFriendshipPo friendship) {
        return friendship.getRequesterUserId().equals(friendship.getUserLoId())
                ? friendship.getUserHiId()
                : friendship.getUserLoId();
    }

    private Long peerUserId(ImFriendshipPo friendship, Long viewerUserId) {
        if (viewerUserId.equals(friendship.getUserLoId())) {
            return friendship.getUserHiId();
        }
        if (viewerUserId.equals(friendship.getUserHiId())) {
            return friendship.getUserLoId();
        }
        throw new ImException("IM_FRIEND_REQUEST_VIEW_FORBIDDEN");
    }

    private UserDirectoryItemResponse requireAvailableUser(Long userId) {
        return userDirectoryFacade.findEnabledById(userId)
                .orElseThrow(() -> new ImException("IM_FRIEND_USER_NOT_FOUND"));
    }

    private ImPrincipal requirePrincipal(ImPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new ImException("IM_PRINCIPAL_REQUIRED");
        }
        return principal;
    }

    private Long requireUserId(Long userId, String code) {
        if (userId == null) {
            throw new ImException(code);
        }
        return userId;
    }

    private void requireDifferentUsers(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new ImException("IM_FRIEND_SELF_DENIED");
        }
    }

    private PairUsers pairUsers(Long firstUserId, Long secondUserId) {
        return firstUserId < secondUserId
                ? new PairUsers(firstUserId, secondUserId)
                : new PairUsers(secondUserId, firstUserId);
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }

    private String normalizeBox(String box) {
        return StringUtils.hasText(box) ? box.trim().toUpperCase(java.util.Locale.ROOT) : "INCOMING";
    }

    private String normalizeText(String value, int maxLength, String errorCode) {
        String normalized = StringUtils.hasText(value) ? value.trim() : "";
        if (normalized.length() > maxLength) {
            throw new ImException(errorCode);
        }
        return normalized;
    }

    private <T> T retryOnUniqueConflict(Supplier<T> action) {
        for (int attempt = 1; attempt <= RETRY_LIMIT; attempt++) {
            try {
                return retryTransactionOperations.execute(status -> action.get());
            } catch (DataIntegrityViolationException ex) {
                if (attempt == RETRY_LIMIT) {
                    throw new ImException("IM_FRIEND_REQUEST_CONFLICT");
                }
            }
        }
        throw new IllegalStateException("IM_FRIEND_REQUEST_RETRY_EXHAUSTED");
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    private record PairUsers(Long userLoId, Long userHiId) {
    }
}
