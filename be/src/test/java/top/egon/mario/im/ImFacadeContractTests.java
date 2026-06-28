package top.egon.mario.im;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.ImFacade;
import top.egon.mario.im.facade.RoomFacade;
import top.egon.mario.im.facade.dto.command.CreateChannelCommand;
import top.egon.mario.im.facade.dto.command.CreateGroupCommand;
import top.egon.mario.im.facade.dto.command.MintWsTicketCommand;
import top.egon.mario.im.facade.dto.query.AuditHistoryQuery;
import top.egon.mario.im.facade.dto.query.ConversationMemberQuery;
import top.egon.mario.im.facade.dto.query.ConversationSurfaceQuery;
import top.egon.mario.im.facade.dto.query.ListChannelsQuery;
import top.egon.mario.im.facade.dto.query.ListGroupsQuery;
import top.egon.mario.im.facade.dto.view.ChannelView;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.facade.dto.view.ConversationSurfaceView;
import top.egon.mario.im.facade.dto.view.GroupView;
import top.egon.mario.im.facade.dto.view.MessageView;
import top.egon.mario.im.facade.dto.view.WsTicketView;
import top.egon.mario.im.facade.mapper.ImFacadeMapper;
import top.egon.mario.im.po.ImConversationPo;
import top.egon.mario.im.po.enums.ImConversationStatus;
import top.egon.mario.im.po.enums.ImConversationType;
import top.egon.mario.im.po.enums.ImSurfaceType;
import top.egon.mario.im.policy.ImPrincipal;
import top.egon.mario.im.service.ConversationService;
import top.egon.mario.im.service.DmService;
import top.egon.mario.im.service.GovernanceService;
import top.egon.mario.im.service.ImTicketService;
import top.egon.mario.im.service.MembershipService;
import top.egon.mario.im.service.MessageService;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImFacadeContractTests {

    private static final String FACADE_PACKAGE = "top.egon.mario.im.facade";
    private static final String COMMAND_PACKAGE = FACADE_PACKAGE + ".dto.command";
    private static final String QUERY_PACKAGE = FACADE_PACKAGE + ".dto.query";
    private static final String VIEW_PACKAGE = FACADE_PACKAGE + ".dto.view";

    private static final List<String> FACADE_TYPES = List.of(
            FACADE_PACKAGE + ".ImFacade",
            FACADE_PACKAGE + ".RoomFacade",
            FACADE_PACKAGE + ".DmFacade",
            FACADE_PACKAGE + ".GovFacade"
    );

    private static final List<String> COMMAND_TYPES = List.of(
            COMMAND_PACKAGE + ".SendMessageCommand",
            COMMAND_PACKAGE + ".CreateChannelCommand",
            COMMAND_PACKAGE + ".CreateGroupCommand",
            COMMAND_PACKAGE + ".JoinCommand",
            COMMAND_PACKAGE + ".ApproveCommand",
            COMMAND_PACKAGE + ".RejectJoinCommand",
            COMMAND_PACKAGE + ".CancelJoinCommand",
            COMMAND_PACKAGE + ".LeaveCommand",
            COMMAND_PACKAGE + ".MarkReadCommand",
            COMMAND_PACKAGE + ".MuteUserCommand",
            COMMAND_PACKAGE + ".GlobalMuteCommand",
            COMMAND_PACKAGE + ".BlockUserCommand",
            COMMAND_PACKAGE + ".OpenDmCommand",
            COMMAND_PACKAGE + ".AnnounceCommand",
            COMMAND_PACKAGE + ".BanUserCommand",
            COMMAND_PACKAGE + ".MintWsTicketCommand"
    );

    private static final List<String> QUERY_TYPES = List.of(
            QUERY_PACKAGE + ".HistoryQuery",
            QUERY_PACKAGE + ".AuditHistoryQuery",
            QUERY_PACKAGE + ".ListConversationsQuery",
            QUERY_PACKAGE + ".ListChannelsQuery",
            QUERY_PACKAGE + ".ListGroupsQuery",
            QUERY_PACKAGE + ".ConversationSurfaceQuery",
            QUERY_PACKAGE + ".ConversationMemberQuery"
    );

    private static final List<String> VIEW_TYPES = List.of(
            VIEW_PACKAGE + ".MessageView",
            VIEW_PACKAGE + ".ConversationView",
            VIEW_PACKAGE + ".ConversationSurfaceView",
            VIEW_PACKAGE + ".ChannelView",
            VIEW_PACKAGE + ".GroupView",
            VIEW_PACKAGE + ".UnreadView",
            VIEW_PACKAGE + ".JoinResultView",
            VIEW_PACKAGE + ".WsTicketView"
    );

    @Test
    void publicFacadeMethodsUseDtoBoundaryOnly() throws Exception {
        for (String typeName : FACADE_TYPES) {
            Class<?> facadeType = Class.forName(typeName);
            assertThat(facadeType.getPackageName()).isEqualTo(FACADE_PACKAGE);
            for (Method method : facadeType.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers()) || method.isSynthetic()) {
                    continue;
                }
                assertNoForbiddenType(method.getGenericReturnType(), facadeType, method);
                for (Type parameterType : method.getGenericParameterTypes()) {
                    assertNoForbiddenType(parameterType, facadeType, method);
                }
            }
        }
    }

    @Test
    void dtoAndPrincipalTypesArePlainRecords() throws Exception {
        for (String typeName : allContractRecords()) {
            Class<?> dtoType = Class.forName(typeName);
            assertThat(dtoType.isRecord())
                    .describedAs("%s must be a Java record", typeName)
                    .isTrue();
            assertThat(hasPersistenceAnnotation(dtoType.getAnnotations()))
                    .describedAs("%s must not use JPA annotations", typeName)
                    .isFalse();
            for (RecordComponent component : dtoType.getRecordComponents()) {
                assertNoForbiddenType(component.getGenericType(), dtoType, null);
                assertThat(hasPersistenceAnnotation(component.getAnnotations()))
                        .describedAs("%s.%s must not use JPA annotations", typeName, component.getName())
                        .isFalse();
            }
        }
    }

    @Test
    void imPrincipalCarriesOnlyCallerSuppliedIdentityContextAndAttributes() throws Exception {
        Class<?> principalType = Class.forName("top.egon.mario.im.policy.ImPrincipal");

        assertThat(principalType.isRecord()).isTrue();
        assertThat(List.of(principalType.getRecordComponents()).stream()
                .map(RecordComponent::getName))
                .containsExactly("userId", "roleCodes", "contextType", "attributes");
        assertThat(List.of(principalType.getRecordComponents()).stream()
                .map(component -> component.getType().getName()))
                .containsExactly(Long.class.getName(), "java.util.Set", String.class.getName(), "java.util.Map");
    }

    @Test
    void conversationViewExposesNullableLastMessageSummary() throws Exception {
        RecordComponent lastMessage = List.of(Class.forName(VIEW_PACKAGE + ".ConversationView").getRecordComponents())
                .stream()
                .filter(component -> "lastMessage".equals(component.getName()))
                .findFirst()
                .orElse(null);

        assertThat(lastMessage).isNotNull();
        assertThat(lastMessage.getType().getName()).isEqualTo(VIEW_PACKAGE + ".MessageView");
    }

    @Test
    void conversationMapperDefaultsUnreadCountForNonListViews() {
        ImConversationPo conversation = new ImConversationPo();
        conversation.setConversationType(ImConversationType.DM);
        conversation.setOwnerSurfaceType(ImSurfaceType.DM_PAIR);
        conversation.setOwnerSurfaceId(1L);
        conversation.setContextType("IM_FACADE_CONTRACT_TEST");
        conversation.setMessageSeq(0L);
        conversation.setStatus(ImConversationStatus.ACTIVE);

        ConversationView view = new ImFacadeMapper().toConversationView(conversation);

        assertThat(view.unreadCount()).isZero();
        assertThat(view.lastMessage()).isNull();
    }

    @Test
    void auditHistoryQueryCarriesAuditPrincipalAtBoundary() throws Exception {
        Class<?> queryType = Class.forName(QUERY_PACKAGE + ".AuditHistoryQuery");

        assertThat(List.of(queryType.getRecordComponents()).stream()
                .map(RecordComponent::getName))
                .containsExactly("principal", "conversationId", "page", "size", "beforeSeq", "afterSeq");
        assertThat(List.of(queryType.getRecordComponents()).stream()
                .map(component -> component.getType().getName()))
                .containsExactly("top.egon.mario.im.policy.ImPrincipal", Long.class.getName(),
                        "int", "int", Long.class.getName(), Long.class.getName());
    }

    @Test
    void facadeShellsExposeExpectedDtoMethods() throws Exception {
        assertMethod(FACADE_PACKAGE + ".ImFacade", "send",
                VIEW_PACKAGE + ".MessageView", COMMAND_PACKAGE + ".SendMessageCommand");
        assertMethod(FACADE_PACKAGE + ".ImFacade", "history",
                "org.springframework.data.domain.Page", QUERY_PACKAGE + ".HistoryQuery");
        assertMethod(FACADE_PACKAGE + ".ImFacade", "auditHistory",
                "org.springframework.data.domain.Page", QUERY_PACKAGE + ".AuditHistoryQuery");
        assertMethod(FACADE_PACKAGE + ".ImFacade", "markRead",
                VIEW_PACKAGE + ".UnreadView", COMMAND_PACKAGE + ".MarkReadCommand");
        assertMethod(FACADE_PACKAGE + ".ImFacade", "listConversations",
                "java.util.List", QUERY_PACKAGE + ".ListConversationsQuery");
        assertMethod(FACADE_PACKAGE + ".ImFacade", "findConversationSurface",
                "java.util.Optional", QUERY_PACKAGE + ".ConversationSurfaceQuery");
        assertMethod(FACADE_PACKAGE + ".ImFacade", "hasActiveConversationMember",
                "boolean", QUERY_PACKAGE + ".ConversationMemberQuery");
        assertMethod(FACADE_PACKAGE + ".ImFacade", "mintWsTicket",
                VIEW_PACKAGE + ".WsTicketView", COMMAND_PACKAGE + ".MintWsTicketCommand");

        assertMethod(FACADE_PACKAGE + ".RoomFacade", "createChannel",
                VIEW_PACKAGE + ".ChannelView", COMMAND_PACKAGE + ".CreateChannelCommand");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "createGroup",
                VIEW_PACKAGE + ".GroupView", COMMAND_PACKAGE + ".CreateGroupCommand");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "applyJoin",
                VIEW_PACKAGE + ".JoinResultView", COMMAND_PACKAGE + ".JoinCommand");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "approveJoin",
                VIEW_PACKAGE + ".JoinResultView", COMMAND_PACKAGE + ".ApproveCommand");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "rejectJoin",
                VIEW_PACKAGE + ".JoinResultView", COMMAND_PACKAGE + ".RejectJoinCommand");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "cancelJoin",
                VIEW_PACKAGE + ".JoinResultView", COMMAND_PACKAGE + ".CancelJoinCommand");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "leave",
                "void", COMMAND_PACKAGE + ".LeaveCommand");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "listChannels",
                "java.util.List", QUERY_PACKAGE + ".ListChannelsQuery");
        assertMethod(FACADE_PACKAGE + ".RoomFacade", "listGroups",
                "java.util.List", QUERY_PACKAGE + ".ListGroupsQuery");

        assertMethod(FACADE_PACKAGE + ".DmFacade", "openDm",
                VIEW_PACKAGE + ".ConversationView", COMMAND_PACKAGE + ".OpenDmCommand");
        assertMethod(FACADE_PACKAGE + ".DmFacade", "block",
                "void", COMMAND_PACKAGE + ".BlockUserCommand");
        assertMethod(FACADE_PACKAGE + ".DmFacade", "unblock",
                "void", COMMAND_PACKAGE + ".BlockUserCommand");

        assertMethod(FACADE_PACKAGE + ".GovFacade", "mute",
                "void", COMMAND_PACKAGE + ".MuteUserCommand");
        assertMethod(FACADE_PACKAGE + ".GovFacade", "globalMute",
                "void", COMMAND_PACKAGE + ".GlobalMuteCommand");
        assertMethod(FACADE_PACKAGE + ".GovFacade", "announce",
                "void", COMMAND_PACKAGE + ".AnnounceCommand");
        assertMethod(FACADE_PACKAGE + ".GovFacade", "ban",
                "void", COMMAND_PACKAGE + ".BanUserCommand");
    }

    @Test
    void imRoomFacadeHasUniqueBeanName() throws Exception {
        Component component = Class.forName(FACADE_PACKAGE + ".RoomFacade").getAnnotation(Component.class);

        assertThat(component).isNotNull();
        assertThat(component.value()).isEqualTo("imRoomFacade");
    }

    @Test
    void dmFacadeUsesConstructorInjectedServiceOnly() throws Exception {
        Constructor<?>[] constructors = Class.forName(FACADE_PACKAGE + ".DmFacade").getDeclaredConstructors();

        assertThat(constructors).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(DmService.class));
    }

    @Test
    void imFacadeUsesConstructorInjectedServicesOnly() throws Exception {
        Constructor<?>[] constructors = Class.forName(FACADE_PACKAGE + ".ImFacade").getDeclaredConstructors();

        assertThat(constructors).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(MessageService.class, ConversationService.class, ImTicketService.class));
    }

    @Test
    void roomFacadeUsesConstructorInjectedServicesOnly() throws Exception {
        Constructor<?>[] constructors = Class.forName(FACADE_PACKAGE + ".RoomFacade").getDeclaredConstructors();

        assertThat(constructors).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(MembershipService.class, ConversationService.class));
    }

    @Test
    void govFacadeUsesConstructorInjectedServiceOnly() throws Exception {
        Constructor<?>[] constructors = Class.forName(FACADE_PACKAGE + ".GovFacade").getDeclaredConstructors();

        assertThat(constructors).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(GovernanceService.class));
    }

    @Test
    void imFacadeDelegatesTicketMintingToTicketService() {
        ImTicketService ticketService = mock(ImTicketService.class);
        MintWsTicketCommand command = new MintWsTicketCommand(
                new ImPrincipal(1L, Set.of("im-user"), "IM_FACADE_CONTRACT_TEST", Map.of()), null);
        WsTicketView view = new WsTicketView("raw-ticket", Instant.EPOCH);
        when(ticketService.mint(command)).thenReturn(view);
        ImFacade imFacade = new ImFacade(null, null, ticketService);

        assertThat(imFacade.mintWsTicket(command)).isSameAs(view);
        verify(ticketService).mint(command);
    }

    @Test
    void roomFacadeDelegatesChannelAndGroupWorkToConversationService() {
        MembershipService membershipService = mock(MembershipService.class);
        ConversationService conversationService = mock(ConversationService.class);
        RoomFacade roomFacade = new RoomFacade(membershipService, conversationService);
        CreateChannelCommand channelCommand = new CreateChannelCommand(
                new ImPrincipal(1L, Set.of("im-user"), "IM_FACADE_CONTRACT_TEST", Map.of()),
                "clocktower", 42L, "town-square", "Town Square", "OPEN", "{}");
        CreateGroupCommand groupCommand = new CreateGroupCommand(
                new ImPrincipal(1L, Set.of("im-user"), "IM_FACADE_CONTRACT_TEST", Map.of()),
                11L, null, null, "storytellers", "Storytellers", "APPROVAL", "{}");
        ListChannelsQuery channelsQuery = new ListChannelsQuery(channelCommand.principal(), "clocktower", 42L);
        ListGroupsQuery groupsQuery = new ListGroupsQuery(groupCommand.principal(), 11L, null, null);
        ChannelView channelView = new ChannelView(
                11L, "clocktower", 42L, "town-square", "Town Square", 1L,
                "PUBLIC", "OPEN", "ACTIVE", "", 21L, 1, Instant.EPOCH);
        GroupView groupView = new GroupView(
                12L, 11L, "clocktower", 42L, "storytellers", "Storytellers", 1L,
                "APPROVAL", "ACTIVE", "", 22L, 1, Instant.EPOCH);
        when(conversationService.createChannel(channelCommand)).thenReturn(channelView);
        when(conversationService.createGroup(groupCommand)).thenReturn(groupView);
        when(conversationService.listChannels(channelsQuery)).thenReturn(List.of(channelView));
        when(conversationService.listGroups(groupsQuery)).thenReturn(List.of(groupView));

        assertThat(roomFacade.createChannel(channelCommand)).isSameAs(channelView);
        assertThat(roomFacade.createGroup(groupCommand)).isSameAs(groupView);
        assertThat(roomFacade.listChannels(channelsQuery)).containsExactly(channelView);
        assertThat(roomFacade.listGroups(groupsQuery)).containsExactly(groupView);
        verify(conversationService).createChannel(channelCommand);
        verify(conversationService).createGroup(groupCommand);
        verify(conversationService).listChannels(channelsQuery);
        verify(conversationService).listGroups(groupsQuery);
    }

    @Test
    void imFacadeDelegatesBoundaryQueriesToServices() {
        MessageService messageService = mock(MessageService.class);
        ConversationService conversationService = mock(ConversationService.class);
        ImFacade imFacade = new ImFacade(messageService, conversationService, null);
        AuditHistoryQuery auditHistoryQuery = new AuditHistoryQuery(
                new ImPrincipal(1L, Set.of("SUPER_ADMIN"), "IM_FACADE_CONTRACT_TEST", Map.of()),
                1L, 0, 20, null, null);
        ConversationSurfaceQuery surfaceQuery = new ConversationSurfaceQuery(1L, null, null);
        ConversationMemberQuery memberQuery = new ConversationMemberQuery(1L, 2L);

        imFacade.auditHistory(auditHistoryQuery);
        imFacade.findConversationSurface(surfaceQuery);
        imFacade.hasActiveConversationMember(memberQuery);

        verify(messageService).auditHistory(auditHistoryQuery);
        verify(conversationService).findConversationSurface(surfaceQuery);
        verify(conversationService).hasActiveConversationMember(memberQuery);
    }

    private static List<String> allContractRecords() {
        return List.of(
                "top.egon.mario.im.policy.ImPrincipal",
                COMMAND_TYPES.get(0), COMMAND_TYPES.get(1), COMMAND_TYPES.get(2), COMMAND_TYPES.get(3),
                COMMAND_TYPES.get(4), COMMAND_TYPES.get(5), COMMAND_TYPES.get(6), COMMAND_TYPES.get(7),
                COMMAND_TYPES.get(8), COMMAND_TYPES.get(9), COMMAND_TYPES.get(10), COMMAND_TYPES.get(11),
                COMMAND_TYPES.get(12), COMMAND_TYPES.get(13), COMMAND_TYPES.get(14), COMMAND_TYPES.get(15),
                QUERY_TYPES.get(0), QUERY_TYPES.get(1), QUERY_TYPES.get(2), QUERY_TYPES.get(3),
                QUERY_TYPES.get(4), QUERY_TYPES.get(5), QUERY_TYPES.get(6),
                VIEW_TYPES.get(0), VIEW_TYPES.get(1), VIEW_TYPES.get(2), VIEW_TYPES.get(3),
                VIEW_TYPES.get(4), VIEW_TYPES.get(5), VIEW_TYPES.get(6), VIEW_TYPES.get(7)
        );
    }

    private static void assertMethod(String ownerTypeName, String methodName,
                                     String returnTypeName, String... parameterTypeNames) throws Exception {
        Class<?> ownerType = Class.forName(ownerTypeName);
        Class<?>[] parameterTypes = new Class<?>[parameterTypeNames.length];
        for (int i = 0; i < parameterTypeNames.length; i++) {
            parameterTypes[i] = Class.forName(parameterTypeNames[i]);
        }

        Method method = ownerType.getMethod(methodName, parameterTypes);
        assertThat(method.getReturnType().getName()).isEqualTo(returnTypeName);
        if ("history".equals(methodName)) {
            assertThat(method.getGenericReturnType().getTypeName()).contains(VIEW_PACKAGE + ".MessageView");
        }
        if (method.getReturnType().equals(List.class)) {
            assertThat(method.getGenericReturnType().getTypeName()).startsWith("java.util.List<");
        }
    }

    private static void assertNoForbiddenType(Type type, Class<?> ownerType, Method method) {
        assertThat(containsForbiddenPackage(type.getTypeName()))
                .describedAs("%s%s leaks %s",
                        ownerType.getName(),
                        method == null ? "" : "." + method.getName(),
                        type.getTypeName())
                .isFalse();
    }

    private static boolean containsForbiddenPackage(String typeName) {
        return typeName.contains("top.egon.mario.im.po.")
                || typeName.contains("top.egon.mario.im.repository.")
                || typeName.contains("jakarta.persistence.")
                || typeName.contains("javax.persistence.")
                || typeName.contains("org.springframework.security.");
    }

    private static boolean hasPersistenceAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            String name = annotation.annotationType().getName();
            if (name.startsWith("jakarta.persistence.") || name.startsWith("javax.persistence.")) {
                return true;
            }
        }
        return false;
    }
}
