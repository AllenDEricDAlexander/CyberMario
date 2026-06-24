import {ReloadOutlined} from '@ant-design/icons'
import {Button, Card, Descriptions, Empty, Space, Tag, Tabs, Timeline, Typography} from 'antd'
import type {DescriptionsProps} from 'antd'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {useParams} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {DateTimeText} from '../../components/DateTimeText'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {getClocktowerGameReplay} from './clocktowerService'
import type {ClocktowerGameEventResponse, ClocktowerGameReplayResponse} from './clocktowerTypes'

function ReplayPage() {
    const {gameId} = useParams()
    const numericGameId = Number(gameId)
    const [replay, setReplay] = useState<ClocktowerGameReplayResponse | null>(null)
    const [loading, setLoading] = useState(false)

    const loadReplay = useCallback(async () => {
        if (!Number.isFinite(numericGameId)) {
            setReplay(null)
            return
        }
        setLoading(true)
        try {
            setReplay(await getClocktowerGameReplay(numericGameId))
        } catch (caught) {
            setReplay(null)
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [numericGameId])

    useEffect(() => {
        void loadReplay()
    }, [loadReplay])

    const publicEvents = useMemo(
        () => (replay?.events ?? []).filter((event) => event.visibility === 'PUBLIC'),
        [replay],
    )
    const privateEvents = useMemo(
        () => (replay?.events ?? []).filter((event) => event.visibility !== 'PUBLIC'),
        [replay],
    )

    return (
        <>
            <PageToolbar
                actions={(
                    <Button
                        disabled={!Number.isFinite(numericGameId)}
                        icon={<ReloadOutlined/>}
                        loading={loading}
                        onClick={voidify(loadReplay)}
                    >
                        刷新
                    </Button>
                )}
                description="按游戏 ID 查看公开、私密与审计可见事件。"
                title="钟楼回放"
            />
            <Card loading={loading} style={{marginBottom: 16}} title="游戏信息">
                <Descriptions bordered column={{xs: 1, sm: 2, lg: 3}} items={metadataItems(replay, numericGameId)}/>
            </Card>
            <Card>
                <Tabs
                    items={[
                        {
                            key: 'public',
                            label: '公开事件',
                            children: <GameEventTimeline events={publicEvents}/>,
                        },
                        {
                            key: 'private',
                            label: '私密事件',
                            children: <GameEventTimeline events={privateEvents}/>,
                        },
                        {
                            key: 'all',
                            label: '全量可见',
                            children: <GameEventTimeline events={replay?.events ?? []}/>,
                        },
                    ]}
                />
            </Card>
        </>
    )
}

function metadataItems(
    replay: ClocktowerGameReplayResponse | null,
    numericGameId: number,
): DescriptionsProps['items'] {
    return [
        {key: 'gameId', label: '游戏 ID', children: replay?.gameId ?? (Number.isFinite(numericGameId) ? numericGameId : '-')},
        {key: 'roomId', label: '房间', children: replay ? `#${replay.roomId}` : '-'},
        {key: 'viewerMode', label: '视角', children: replay?.viewerMode ?? '-'},
        {key: 'eventCount', label: '事件数', children: replay?.events.length ?? '-'},
    ]
}

function GameEventTimeline({events}: { events: ClocktowerGameEventResponse[] }) {
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
                        <Tag>#{event.eventSeq}</Tag>
                        <Tag color="blue">{event.eventType}</Tag>
                        <Tag color={visibilityColor(event.visibility)}>{event.visibility}</Tag>
                    </Space>
                ),
                content: (
                    <Space orientation="vertical" size={4} style={{width: '100%'}}>
                        <Typography.Text type="secondary">
                            {event.phase} · 第 {event.dayNo} 天 / 第 {event.nightNo} 夜 · <DateTimeText value={event.occurredAt}/>
                        </Typography.Text>
                        <Space wrap>
                            <Tag>actor={event.actorGameSeatId ?? '-'}</Tag>
                            <Tag>target={event.targetGameSeatId ?? '-'}</Tag>
                            <Tag>visible={event.visibleGameSeatIds.length > 0 ? event.visibleGameSeatIds.join(',') : '-'}</Tag>
                        </Space>
                        <Typography.Paragraph copyable style={{marginBottom: 0, whiteSpace: 'pre-wrap'}}>
                            {formatPayload(event.payload)}
                        </Typography.Paragraph>
                    </Space>
                ),
            }))}
        />
    )
}

function visibilityColor(visibility: string) {
    if (visibility === 'PUBLIC') {
        return 'success'
    }
    if (visibility === 'PRIVATE') {
        return 'processing'
    }
    if (visibility === 'STORYTELLER') {
        return 'warning'
    }
    return 'default'
}

function formatPayload(payload: Record<string, unknown>) {
    if (!payload || Object.keys(payload).length === 0) {
        return '{}'
    }
    return JSON.stringify(payload, null, 2)
}

export const Component = ReplayPage
