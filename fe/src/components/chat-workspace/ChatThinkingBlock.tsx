import {Think} from '@ant-design/x'
import {Typography} from 'antd'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

const markdownPlugins = [remarkGfm]

export type ChatThinkingBlockProps = {
    content?: string
    loading?: boolean
    defaultExpanded?: boolean
}

export function ChatThinkingBlock(props: ChatThinkingBlockProps) {
    const {content = '', loading = false, defaultExpanded} = props
    const hasContent = content.trim().length > 0

    return (
        <Think
            className="chat-workspace-x-thinking"
            defaultExpanded={defaultExpanded ?? loading}
            loading={loading}
            title={loading ? '正在推理…' : '推理过程'}
        >
            {hasContent ? (
                <div className="chat-workspace-x-thinking-content">
                    <ReactMarkdown remarkPlugins={markdownPlugins}>{content}</ReactMarkdown>
                </div>
            ) : (
                <Typography.Text type="secondary">
                    {loading ? '推理内容生成中…' : '本次回答没有推理过程。'}
                </Typography.Text>
            )}
        </Think>
    )
}
