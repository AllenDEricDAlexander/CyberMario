package top.egon.mario.agent.externalim.flow;

import org.junit.jupiter.api.Test;
import top.egon.mario.agent.externalim.ExternalChatException;
import top.egon.mario.agent.externalim.model.ChatInvocation;
import top.egon.mario.agent.externalim.model.ChatSource;
import top.egon.mario.agent.externalim.model.ExternalChatPlatform;
import top.egon.mario.agent.externalim.model.ExternalConversationType;
import top.egon.mario.agent.externalim.model.ExternalMessageType;
import top.egon.mario.agent.externalim.model.ExternalSender;
import top.egon.mario.agent.externalim.model.ExternalSenderType;
import top.egon.mario.pojo.request.ChatRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatInvocationPolicyTests {

    private final ChatInvocationPolicy policy = new DefaultChatInvocationPolicy();

    @Test
    void fromWebUsesAuthenticatedOwnerAndPrefersSessionId() {
        RbacPrincipal principal = new RbacPrincipal(8L, "luigi", Set.of(), Set.of(), "v1");

        ChatInvocation invocation = policy.fromWeb(
                new ChatRequest(" 你好 ", "thread-1", "session-1", true, " space-1 "), principal);

        assertThat(invocation.source()).isEqualTo(ChatSource.WEB);
        assertThat(invocation.message()).isEqualTo(" 你好 ");
        assertThat(invocation.ownerUserId()).isEqualTo(8L);
        assertThat(invocation.ownerUsername()).isEqualTo("luigi");
        assertThat(invocation.webSessionId()).isEqualTo("session-1");
        assertThat(invocation.memorySpaceId()).isEqualTo("space-1");
    }

    @Test
    void fromWebNeverAcceptsOwnerIdentityFromRequestData() {
        ChatInvocation invocation = policy.fromWeb(
                new ChatRequest("你好", "thread-1", null, true, "space-1"), null);

        assertThat(invocation.ownerUserId()).isNull();
        assertThat(invocation.ownerUsername()).isNull();
    }

    @Test
    void requireExternalAcceptsCompleteTrustedInvocation() {
        ChatInvocation invocation = externalInvocation("event-1");

        assertThat(policy.requireExternal(invocation)).isSameAs(invocation);
    }

    @Test
    void requireExternalFailsClosedWhenEventIdentityIsMissing() {
        ChatInvocation invocation = externalInvocation(" ");

        assertThatThrownBy(() -> policy.requireExternal(invocation))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("EXTERNAL_CHAT_INVOCATION_INVALID");
    }

    @Test
    void requireExternalRejectsEveryTrustedBindingIdentityGap() {
        assertInvalid(externalInvocation(null, "space-1", ExternalChatPlatform.TELEGRAM,
                "connector-1", "group-1", "group:group-1", "event-1"));
        assertInvalid(externalInvocation(8L, null, ExternalChatPlatform.TELEGRAM,
                "connector-1", "group-1", "group:group-1", "event-1"));
        assertInvalid(externalInvocation(8L, "space-1", null,
                "connector-1", "group-1", "group:group-1", "event-1"));
        assertInvalid(externalInvocation(8L, "space-1", ExternalChatPlatform.TELEGRAM,
                null, "group-1", "group:group-1", "event-1"));
        assertInvalid(externalInvocation(8L, "space-1", ExternalChatPlatform.TELEGRAM,
                "connector-1", null, "group:group-1", "event-1"));
        assertInvalid(externalInvocation(8L, "space-1", ExternalChatPlatform.TELEGRAM,
                "connector-1", "group-1", null, "event-1"));
        assertInvalid(externalInvocation(8L, "space-1", ExternalChatPlatform.TELEGRAM,
                "connector-1", "group-1", "group:group-1", null));
    }

    private void assertInvalid(ChatInvocation invocation) {
        assertThatThrownBy(() -> policy.requireExternal(invocation))
                .isInstanceOf(ExternalChatException.class)
                .extracting(error -> ((ExternalChatException) error).code())
                .isEqualTo("EXTERNAL_CHAT_INVOCATION_INVALID");
    }

    private ChatInvocation externalInvocation(String eventId) {
        return externalInvocation(8L, "space-1", ExternalChatPlatform.TELEGRAM,
                "connector-1", "group-1", "group:group-1", eventId);
    }

    private ChatInvocation externalInvocation(Long ownerUserId, String memorySpaceId,
                                              ExternalChatPlatform platform, String connectorId,
                                              String conversationId, String audienceKey,
                                              String eventId) {
        return new ChatInvocation(ChatSource.EXTERNAL_IM, "你好", ownerUserId, null, null,
                memorySpaceId, platform, connectorId, conversationId,
                ExternalConversationType.GROUP, audienceKey,
                new ExternalSender("sender-1", "Mario", ExternalSenderType.HUMAN),
                ExternalMessageType.TEXT, false, false, eventId, "message-1", Instant.now());
    }
}
