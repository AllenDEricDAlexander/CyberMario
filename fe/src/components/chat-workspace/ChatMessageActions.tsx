import {
    CopyOutlined,
    DislikeOutlined,
    LikeOutlined,
    ReloadOutlined,
    SearchOutlined,
    StopOutlined,
} from '@ant-design/icons'
import {Actions} from '@ant-design/x'
import {Button} from 'antd'
import {useMemo} from 'react'
import type {ActionsProps} from '@ant-design/x'
import type {RagFeedbackType} from '../../modules/rag/ragTypes'

type ActionItem = ActionsProps['items'][number]
type MessageActionKey = 'copy' | 'reload' | RagFeedbackType

type MessageActionDefinition = {
    key: MessageActionKey
    label: string
    icon: ActionItem['icon']
}

export type ChatMessageActionsProps = {
    onCopy?: () => void
    onReload?: () => void
    canFeedback?: boolean
    onFeedback?: (feedbackType: RagFeedbackType) => void
}

const feedbackActions: Array<MessageActionDefinition & {key: RagFeedbackType}> = [
    {key: 'HELPFUL', label: 'Helpful', icon: <LikeOutlined/>},
    {key: 'NOT_HELPFUL', label: 'Not helpful', icon: <DislikeOutlined/>},
    {key: 'BAD_SOURCE', label: 'Bad source', icon: <SearchOutlined/>},
    {key: 'NO_ANSWER', label: 'No answer', icon: <StopOutlined/>},
]

export function ChatMessageActions(props: ChatMessageActionsProps) {
    const {onCopy, onReload, canFeedback = false, onFeedback} = props

    const items = useMemo<ActionsProps['items']>(() => {
        const nextItems: ActionsProps['items'] = []

        if (onCopy) {
            nextItems.push(createActionItem(
                {key: 'copy', label: 'Copy', icon: <CopyOutlined/>},
                onCopy
            ))
        }

        if (onReload) {
            nextItems.push(createActionItem(
                {key: 'reload', label: 'Retry', icon: <ReloadOutlined/>},
                onReload
            ))
        }

        if (canFeedback && onFeedback) {
            nextItems.push(...feedbackActions.map(action => createActionItem(
                action,
                () => onFeedback(action.key)
            )))
        }

        return nextItems
    }, [canFeedback, onCopy, onFeedback, onReload])

    if (items.length === 0) {
        return null
    }

    return (
        <Actions
            className="chat-workspace-x-message-actions"
            items={items}
            variant="borderless"
        />
    )
}

function createActionItem(action: MessageActionDefinition, onClick: () => void): ActionItem {
    return {
        key: action.key,
        label: action.label,
        icon: action.icon,
        actionRender: () => (
            <Button
                aria-label={action.label}
                className="chat-workspace-x-message-action-button"
                icon={action.icon}
                size="small"
                type="text"
                onClick={onClick}
            >
                {action.label}
            </Button>
        ),
    }
}
