import {Alert, Empty, Typography} from 'antd'
import {Bubble} from '@ant-design/x'
import {useMemo} from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type {BubbleListProps} from '@ant-design/x'
import type {RagFeedbackType, SourceReferenceResponse} from '../../modules/rag/ragTypes'
import {mapWorkspaceMessageToBubbleItem} from './chatWorkspaceMappers'
import {ChatMessageActions} from './ChatMessageActions'
import {ChatSourcesPanel} from './ChatSourcesPanel'
import {ChatThinkingBlock} from './ChatThinkingBlock'
import type {ChatWorkspaceBubbleItem, ChatWorkspaceMessage} from './chatWorkspaceTypes'

const markdownPlugins = [remarkGfm]

export type ChatMessageListProps = {
    messages: ChatWorkspaceMessage[]
    sending?: boolean
    canFeedback?: boolean
    canFeedbackMessage?: (message: ChatWorkspaceMessage) => boolean
    canReloadMessage?: (message: ChatWorkspaceMessage) => boolean
    onSourceSelect?: (source: SourceReferenceResponse) => void
    onFeedback?: (message: ChatWorkspaceMessage, feedbackType: RagFeedbackType) => void
    onCopy?: (message: ChatWorkspaceMessage) => void
    onReload?: (message: ChatWorkspaceMessage) => void
}

const roles: BubbleListProps['role'] = {
    ai: {
        placement: 'start',
        className: 'chat-workspace-x-bubble chat-workspace-x-bubble-ai',
    },
    user: {
        placement: 'end',
        className: 'chat-workspace-x-bubble chat-workspace-x-bubble-user',
    },
    system: {
        placement: 'start',
        className: 'chat-workspace-x-bubble chat-workspace-x-bubble-system',
    },
}

export function ChatMessageList(props: ChatMessageListProps) {
    const {
        messages,
        sending = false,
        canFeedback = false,
        canFeedbackMessage,
        canReloadMessage,
        onSourceSelect,
        onFeedback,
        onCopy,
        onReload,
    } = props

    const items = useMemo<ChatWorkspaceBubbleItem[]>(
        () => messages.map((message, index) => {
            const isLastMessage = index === messages.length - 1
            const isPending = isMessagePending(message, sending && isLastMessage)

            return {
                ...mapWorkspaceMessageToBubbleItem(message),
                loading: message.role === 'assistant' && isPending && !message.content.trim(),
                contentRender: () => renderMessageContent({
                    canFeedback,
                    canFeedbackMessage,
                    canReloadMessage,
                    isPending,
                    message,
                    onCopy,
                    onFeedback,
                    onReload,
                    onSourceSelect,
                }),
            }
        }),
        [canFeedback, canFeedbackMessage, canReloadMessage, messages, onCopy, onFeedback, onReload, onSourceSelect, sending]
    )

    if (items.length === 0) {
        return (
            <div className="chat-workspace-x-message-list chat-workspace-x-message-list-empty">
                <Empty description="No messages yet" image={Empty.PRESENTED_IMAGE_SIMPLE}/>
            </div>
        )
    }

    return (
        <div className="chat-workspace-x-message-list" aria-live="polite">
            <Bubble.List
                autoScroll
                className="chat-workspace-x-bubble-list"
                items={items}
                role={roles}
            />
        </div>
    )
}

type RenderMessageContentOptions = {
    message: ChatWorkspaceMessage
    isPending: boolean
    canFeedback: boolean
    canFeedbackMessage?: (message: ChatWorkspaceMessage) => boolean
    canReloadMessage?: (message: ChatWorkspaceMessage) => boolean
    onSourceSelect?: (source: SourceReferenceResponse) => void
    onFeedback?: (message: ChatWorkspaceMessage, feedbackType: RagFeedbackType) => void
    onCopy?: (message: ChatWorkspaceMessage) => void
    onReload?: (message: ChatWorkspaceMessage) => void
}

function renderMessageContent(options: RenderMessageContentOptions) {
    const {
        message,
        isPending,
        canFeedback,
        canFeedbackMessage,
        canReloadMessage,
        onSourceSelect,
        onFeedback,
        onCopy,
        onReload,
    } = options
    const hasContent = message.content.trim().length > 0
    const hasThinking = Boolean(message.thinkContent?.trim())
    const messageCanReceiveFeedback = defaultCanFeedbackMessage(message) &&
        (canFeedbackMessage?.(message) ?? true)
    const showFeedback = canFeedback && hasContent && messageCanReceiveFeedback
    const showReload = Boolean(onReload && (canReloadMessage?.(message) ?? true))
    const showActions = Boolean(onCopy || showReload || (showFeedback && onFeedback))

    return (
        <div className="chat-workspace-x-message-content">
            {hasThinking && (
                <ChatThinkingBlock
                    content={message.thinkContent}
                    defaultExpanded={isPending && !hasContent}
                    loading={isPending && !hasContent}
                />
            )}

            {hasContent ? (
                <div className="chat-workspace-x-message-markdown">
                    <ReactMarkdown remarkPlugins={markdownPlugins}>{message.content}</ReactMarkdown>
                </div>
            ) : isPending ? (
                <Typography.Text type="secondary">Generating response...</Typography.Text>
            ) : message.error ? (
                <Alert showIcon title={message.error} type="error"/>
            ) : null}

            {message.error && hasContent && (
                <Alert
                    className="chat-workspace-x-message-error"
                    showIcon
                    title={message.error}
                    type="error"
                />
            )}

            <ChatSourcesPanel
                sources={message.sources}
                title="Referenced sources"
                onSourceSelect={onSourceSelect}
            />

            {showActions && (
                <div className="chat-workspace-x-message-footer">
                    <ChatMessageActions
                        canFeedback={showFeedback}
                        onCopy={onCopy ? () => onCopy(message) : undefined}
                        onFeedback={onFeedback ? feedbackType => onFeedback(message, feedbackType) : undefined}
                        onReload={showReload && onReload ? () => onReload(message) : undefined}
                    />
                </div>
            )}
        </div>
    )
}

function isMessagePending(message: ChatWorkspaceMessage, isSendingLastMessage: boolean): boolean {
    return message.status === 'loading' || message.status === 'updating' || isSendingLastMessage
}

function defaultCanFeedbackMessage(message: ChatWorkspaceMessage): boolean {
    if (message.role !== 'assistant' || message.id === 'welcome' || !message.content.trim()) {
        return false
    }

    return Boolean(
        message.traceId ||
        message.messageId ||
        message.question ||
        (message.sources && message.sources.length > 0)
    )
}
