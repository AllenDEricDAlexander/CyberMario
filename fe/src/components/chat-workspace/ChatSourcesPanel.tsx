import {FileSearchOutlined} from '@ant-design/icons'
import {Sources} from '@ant-design/x'
import {Button, Space, Tag, Typography} from 'antd'
import {useMemo} from 'react'
import type {SourcesProps} from '@ant-design/x'
import type {SourceReferenceResponse} from '../../modules/rag/ragTypes'

type SourceItem = NonNullable<SourcesProps['items']>[number]

export type ChatSourcesPanelProps = {
    sources?: SourceReferenceResponse[]
    title?: string
    inline?: boolean
    defaultExpanded?: boolean
    onSourceSelect?: (source: SourceReferenceResponse) => void
}

export function ChatSourcesPanel(props: ChatSourcesPanelProps) {
    const {
        sources = [],
        title = 'Sources',
        inline = false,
        defaultExpanded = false,
        onSourceSelect,
    } = props

    const items = useMemo<SourceItem[]>(
        () => sources.map((source, index) => {
            const key = `${source.sourceId}-${source.chunkId}-${index}`

            return {
                key,
                icon: <FileSearchOutlined/>,
                title: source.documentName || `Document ${source.documentId}`,
                description: (
                    <Space className="chat-workspace-x-source-meta" wrap>
                        <Typography.Text type="secondary">{source.knowledgeBaseName}</Typography.Text>
                        <Tag>chunk {source.chunkIndex}</Tag>
                        <Tag>score {source.score.toFixed(4)}</Tag>
                        {source.matchedBy && <Tag>{source.matchedBy}</Tag>}
                    </Space>
                ),
            }
        }),
        [sources]
    )

    if (items.length === 0) {
        return null
    }

    return (
        <div className="chat-workspace-x-sources-panel">
            <Sources
                className="chat-workspace-x-sources"
                defaultExpanded={defaultExpanded}
                inline={inline}
                items={items}
                title={`${title} (${items.length})`}
            />
            {onSourceSelect && (
                <div className="chat-workspace-x-source-actions" role="list" aria-label="Select sources">
                    {sources.map((source, index) => (
                        <div key={`${source.sourceId}-${source.chunkId}-${index}-button`} role="listitem">
                            <Button
                                className="chat-workspace-x-source-button"
                                icon={<FileSearchOutlined/>}
                                size="small"
                                type="text"
                                onClick={() => onSourceSelect(source)}
                            >
                                {`Open source ${index + 1}: ${source.documentName || `Document ${source.documentId}`}`}
                            </Button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    )
}
