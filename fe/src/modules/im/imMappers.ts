import type {ChatWorkspaceConversation, ChatWorkspaceMessage} from '../../components/chat-workspace/chatWorkspaceTypes'
import type {ConversationView, MessageView} from './imTypes'

type MessageMappingOptions = {
    currentUserId?: number
}

export function mapImConversationToWorkspaceConversation(
    conversation: ConversationView,
): ChatWorkspaceConversation & {imConversation: ConversationView; unreadCount: number} {
    return {
        key: `im-conversation-${conversation.id}`,
        label: conversationLabel(conversation),
        description: conversation.lastMessage?.content ?? conversation.conversationType,
        group: conversation.conversationType,
        updatedAt: conversation.lastActiveAt ?? conversation.lastMessageAt ?? undefined,
        imConversation: conversation,
        unreadCount: conversation.unreadCount ?? 0,
    }
}

export function mapImMessageToWorkspaceMessage(
    message: MessageView,
    options: MessageMappingOptions = {},
): ChatWorkspaceMessage {
    const deleted = Boolean(message.deletedAt)
    const system = deleted || message.messageType === 'SYSTEM' || message.senderUserId === null || message.senderUserId === undefined
    return {
        id: `im-message-${message.id}`,
        role: system ? 'system' : message.senderUserId === options.currentUserId ? 'user' : 'assistant',
        content: deleted ? 'Message deleted' : message.content ?? '',
        status: message.status === 'FAILED' ? 'error' : 'success',
        messageId: String(message.id),
        conversationId: message.conversationId,
        senderUserId: message.senderUserId,
        messageSeq: message.messageSeq,
        clientMsgId: message.clientMsgId,
        messageType: message.messageType,
        payloadJson: message.payloadJson,
        metadataJson: message.metadataJson,
        sentAt: message.sentAt,
        imMessage: message,
    }
}

export function mapImMessagesToWorkspaceMessages(
    messages: MessageView[],
    options: MessageMappingOptions = {},
): ChatWorkspaceMessage[] {
    return [...messages]
        .sort((left, right) => left.messageSeq - right.messageSeq)
        .map((message) => mapImMessageToWorkspaceMessage(message, options))
}

function conversationLabel(conversation: ConversationView) {
    if (conversation.ownerSurfaceType && conversation.ownerSurfaceId) {
        return `${conversation.ownerSurfaceType} #${conversation.ownerSurfaceId}`
    }
    return `${conversation.conversationType} #${conversation.id}`
}
