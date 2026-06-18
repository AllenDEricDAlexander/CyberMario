import {Tag} from 'antd'
import type {ReactNode} from 'react'
import type {RagFeedbackType, SourceReferenceResponse} from '../../modules/rag/ragTypes'
import {ChatConversationSidebar} from './ChatConversationSidebar'
import {ChatMessageList} from './ChatMessageList'
import {ChatSender} from './ChatSender'
import {ChatWorkspaceHeader} from './ChatWorkspaceHeader'
import type {ChatWorkspaceConversation, ChatWorkspaceMessage} from './chatWorkspaceTypes'

export type ChatWorkspaceProps = {
    activeConversationKey?: string
    brandTitle: string
    brandDescription?: string
    conversations: ChatWorkspaceConversation[]
    sidebarLoading?: boolean
    title: ReactNode
    subtitle?: ReactNode
    headerActions?: ReactNode
    messages: ChatWorkspaceMessage[]
    sending: boolean
    input: string
    settings?: ReactNode
    canFeedback?: boolean
    canFeedbackMessage?: (message: ChatWorkspaceMessage) => boolean
    onConversationChange: (conversationKey: string) => void
    onNewConversation: () => void
    onReloadConversations?: () => void
    onArchiveConversation?: (conversationKey?: string) => void
    onOpenSettings?: () => void
    onInputChange: (value: string) => void
    onSend: (message: string) => void
    onStop?: () => void
    onSourceSelect?: (source: SourceReferenceResponse) => void
    onFeedback?: (message: ChatWorkspaceMessage, feedbackType: RagFeedbackType) => void
    onCopyMessage?: (message: ChatWorkspaceMessage) => void
    onReloadMessage?: (message: ChatWorkspaceMessage) => void
}

export function ChatWorkspace(props: ChatWorkspaceProps) {
    const {
        activeConversationKey,
        brandTitle,
        brandDescription,
        conversations,
        sidebarLoading,
        title,
        subtitle,
        headerActions,
        messages,
        sending,
        input,
        settings,
        canFeedback = false,
        canFeedbackMessage,
        onConversationChange,
        onNewConversation,
        onReloadConversations,
        onArchiveConversation,
        onOpenSettings,
        onInputChange,
        onSend,
        onStop,
        onSourceSelect,
        onFeedback,
        onCopyMessage,
        onReloadMessage,
    } = props

    return (
        <div className="chat-workspace-x">
            <ChatConversationSidebar
                activeKey={activeConversationKey}
                brandDescription={brandDescription}
                brandTitle={brandTitle}
                conversations={conversations}
                loading={sidebarLoading}
                onActiveChange={onConversationChange}
                onArchive={onArchiveConversation}
                onNewConversation={onNewConversation}
                onReload={onReloadConversations}
            />

            <section className="chat-workspace-x-main" aria-label="Chat workspace">
                <ChatWorkspaceHeader
                    actions={headerActions}
                    messageCount={messages.length}
                    status={sending ? <Tag color="processing">Responding</Tag> : undefined}
                    subtitle={subtitle}
                    title={title}
                    onOpenSettings={onOpenSettings}
                    onReload={onReloadConversations}
                />
                <div className="chat-workspace-x-main-body">
                    <ChatMessageList
                        canFeedback={canFeedback}
                        canFeedbackMessage={canFeedbackMessage}
                        messages={messages}
                        sending={sending}
                        onCopy={onCopyMessage}
                        onFeedback={onFeedback}
                        onReload={onReloadMessage}
                        onSourceSelect={onSourceSelect}
                    />
                </div>
                <div className="chat-workspace-x-main-sender">
                    <ChatSender
                        input={input}
                        sending={sending}
                        onInputChange={onInputChange}
                        onSend={onSend}
                        onStop={onStop}
                    />
                </div>
            </section>

            {settings}
        </div>
    )
}
