import {Empty, Space, Tag, Timeline, Typography} from 'antd'
import {DateTimeText} from '../../../components/DateTimeText'
import type {ClocktowerEventResponse, ClocktowerVisibility} from '../clocktowerTypes'

type EventTimelineProps = {
    events: ClocktowerEventResponse[]
}

const visibilityColors: Record<ClocktowerVisibility, string> = {
    PUBLIC: 'success',
    PRIVATE: 'processing',
    STORYTELLER: 'warning',
    AUDIT: 'default',
}

export function EventTimeline({events}: EventTimelineProps) {
    if (events.length === 0) {
        return <Empty description="暂无事件"/>
    }

    return (
        <Timeline
            items={events.map((event) => ({
                color: event.visibility === 'PUBLIC' ? 'green' : 'blue',
                key: event.eventId,
                title: (
                    <Space wrap>
                        <Tag>#{event.seqNo}</Tag>
                        <Tag color="blue">{event.eventType}</Tag>
                        <Tag color={visibilityColors[event.visibility]}>{event.visibility}</Tag>
                    </Space>
                ),
                content: (
                    <Space orientation="vertical" size={2}>
                        <Typography.Text type="secondary">
                            {event.phase} · 第 {event.dayNo} 天 / 第 {event.nightNo} 夜 · <DateTimeText value={event.createdAt}/>
                        </Typography.Text>
                        <Typography.Text code>{formatPayload(event.payload)}</Typography.Text>
                    </Space>
                ),
            }))}
        />
    )
}

function formatPayload(payload: Record<string, unknown>) {
    if (!payload || Object.keys(payload).length === 0) {
        return '{}'
    }
    return JSON.stringify(payload)
}
