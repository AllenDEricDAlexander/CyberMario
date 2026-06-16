import {InboxOutlined, PlusOutlined, ReloadOutlined} from '@ant-design/icons'
import {Button, Select, Space, Switch, Tag} from 'antd'
import type {AgentMemoryEntryType, AgentMemorySessionResponse} from './agentTypes'

type MemorySessionControlsProps = {
    entryType: AgentMemoryEntryType
    sessions: AgentMemorySessionResponse[]
    sessionId?: string
    memoryEnabled: boolean
    longTermExtractionEnabled?: boolean
    showExtractionSwitch?: boolean
    loading?: boolean
    onCreate: () => void
    onSelect: (sessionId: string) => void
    onSelectSession?: (session: AgentMemorySessionResponse) => void
    onMemoryChange: (enabled: boolean) => void
    onExtractionChange?: (enabled: boolean) => void
    onArchive?: () => void
    onReload?: () => void
}

export function MemorySessionControls(props: MemorySessionControlsProps) {
    return (
        <Space wrap>
            <Select
                allowClear={false}
                loading={props.loading}
                options={props.sessions.map((item) => ({
                    label: item.title || item.sessionId,
                    value: item.sessionId,
                }))}
                placeholder="选择会话"
                style={{minWidth: 220}}
                value={props.sessionId}
                onChange={(value) => {
                    props.onSelect(value)
                    const session = props.sessions.find((item) => item.sessionId === value)
                    if (session) {
                        props.onSelectSession?.(session)
                    }
                }}
            />
            <Button icon={<PlusOutlined/>} onClick={props.onCreate}>
                新会话
            </Button>
            <Switch
                checked={props.memoryEnabled}
                checkedChildren="Memory"
                unCheckedChildren="Memory"
                onChange={props.onMemoryChange}
            />
            {props.showExtractionSwitch && (
                <Switch
                    checked={props.longTermExtractionEnabled}
                    checkedChildren="长期提取"
                    unCheckedChildren="长期提取"
                    onChange={props.onExtractionChange}
                />
            )}
            {props.onArchive && (
                <Button disabled={!props.sessionId} icon={<InboxOutlined/>} onClick={props.onArchive}>
                    归档
                </Button>
            )}
            {props.onReload && <Button aria-label={`${props.entryType} memory refresh`} icon={<ReloadOutlined/>} onClick={props.onReload}/>}
            {props.sessionId && <Tag>{props.sessionId}</Tag>}
        </Space>
    )
}
