import {
    ApartmentOutlined,
    EyeOutlined,
    HistoryOutlined,
    NumberOutlined,
    ReloadOutlined,
    ThunderboltOutlined,
} from '@ant-design/icons'
import {Button, Card, Space, Tag, Tabs, Timeline, Typography} from 'antd'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {useParams} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {DateTimeText} from '../../components/DateTimeText'
import {EmptyState} from '../../components/EmptyState'
import {PageSection, PageStack} from '../../components/PageSection'
import {PageToolbar} from '../../components/PageToolbar'
import {StatCard, StatGrid} from '../../components/StatCard'
import {voidify} from '../../utils/async'
import {getClocktowerGameReplay} from './clocktowerService'
import type {ClocktowerGameEventResponse, ClocktowerGameReplayResponse} from './clocktowerTypes'
import './clocktower.css'

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
                back="/clocktower/replays"
                description="按游戏 ID 查看公开、私密与审计可见事件。"
                icon={<HistoryOutlined/>}
                title="钟楼回放"
            />
            <PageStack>
                <PageSection title="游戏信息">
                    <GameMetadata gameId={numericGameId} loading={loading} replay={replay}/>
                </PageSection>
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
            </PageStack>
        </>
    )
}

function GameMetadata({gameId, loading, replay}: {
    gameId: number
    loading: boolean
    replay: ClocktowerGameReplayResponse | null
}) {
    return (
        <StatGrid>
            <StatCard
                icon={<NumberOutlined/>}
                label="游戏 ID"
                loading={loading}
                value={replay?.gameId ?? (Number.isFinite(gameId) ? gameId : '-')}
            />
            <StatCard
                icon={<ApartmentOutlined/>}
                label="房间"
                loading={loading}
                tone="sky"
                value={replay ? `#${replay.roomId}` : '-'}
            />
            <StatCard
                icon={<EyeOutlined/>}
                label="视角"
                loading={loading}
                tone="violet"
                value={replay?.viewerMode ?? '-'}
            />
            <StatCard
                hint="包含当前视角可见的全部事件"
                icon={<ThunderboltOutlined/>}
                label="事件数"
                loading={loading}
                tone="amber"
                value={replay?.events.length ?? '-'}
            />
        </StatGrid>
    )
}

function GameEventTimeline({events}: { events: ClocktowerGameEventResponse[] }) {
    if (events.length === 0) {
        return <EmptyState description="这一档可见范围内还没有记录任何事件。" inline title="暂无事件"/>
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
                    <Space className="u-full-width" orientation="vertical" size={4}>
                        <Typography.Text type="secondary">
                            {event.phase} · 第 {event.dayNo} 天 / 第 {event.nightNo} 夜 · <DateTimeText value={event.occurredAt}/>
                        </Typography.Text>
                        <Space wrap>
                            <Tag>actor={event.actorGameSeatId ?? '-'}</Tag>
                            <Tag>target={event.targetGameSeatId ?? '-'}</Tag>
                            <Tag>visible={event.visibleGameSeatIds.length > 0 ? event.visibleGameSeatIds.join(',') : '-'}</Tag>
                        </Space>
                        <Typography.Paragraph className="clocktower-payload" copyable>
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
