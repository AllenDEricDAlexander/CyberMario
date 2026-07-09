import {AudioOutlined, ClockCircleOutlined, StepForwardOutlined} from '@ant-design/icons'
import {App, Button, Card, Empty, Flex, Input, List, Space, Tag, Typography} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {
    extendClocktowerMicSession,
    finishClocktowerMicTurn,
    getClocktowerMicSession,
    grabClocktowerMic,
    releaseClocktowerMic,
    skipClocktowerMicTurn,
} from '../clocktowerService'
import type {
    ClocktowerGameEventResponse,
    ClocktowerMicSessionView,
    ClocktowerViewerMode,
} from '../clocktowerTypes'

const SPEECH_MAX_LENGTH = 1000

type MicAction = 'grab' | 'finish' | 'release' | 'skip' | 'extend'

export function PublicMicPanel({
    gameId,
    myGameSeatId,
    onMicChanged,
    onSubmitSpeech,
    viewerMode,
}: {
    gameId: number
    myGameSeatId?: number | null
    viewerMode: ClocktowerViewerMode
    onMicChanged?: (session: ClocktowerMicSessionView) => void
    onSubmitSpeech?: (content: string) => Promise<ClocktowerGameEventResponse | null | undefined>
}) {
    const {message} = App.useApp()
    const [session, setSession] = useState<ClocktowerMicSessionView | null>(null)
    const [loading, setLoading] = useState(false)
    const [actionLoading, setActionLoading] = useState<MicAction | null>(null)
    const [submittingSpeech, setSubmittingSpeech] = useState(false)
    const [now, setNow] = useState(() => new Date())

    const refresh = useCallback(async () => {
        setLoading(true)
        try {
            const next = await getClocktowerMicSession(gameId)
            setSession(next)
            onMicChanged?.(next)
        } catch (caught) {
            reportGlobalError(caught)
            setSession(null)
        } finally {
            setLoading(false)
        }
    }, [gameId, onMicChanged])

    useEffect(() => {
        void refresh()
    }, [refresh])

    useEffect(() => {
        const timer = window.setInterval(() => setNow(new Date()), 1000)
        return () => window.clearInterval(timer)
    }, [])

    async function runMicAction(action: MicAction) {
        if (!session) {
            return
        }
        setActionLoading(action)
        try {
            const next = await executeMicAction(action, gameId, session)
            setSession(next)
            onMicChanged?.(next)
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setActionLoading(null)
        }
    }

    async function submitSpeech(content: string) {
        if (!onSubmitSpeech) {
            return
        }
        setSubmittingSpeech(true)
        try {
            await onSubmitSpeech(content)
            message.success('发言已提交')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setSubmittingSpeech(false)
        }
    }

    return (
        <Card loading={loading} title="公聊麦序">
            <PublicMicPanelContent
                actionLoading={actionLoading}
                gameId={gameId}
                myGameSeatId={myGameSeatId}
                now={now}
                onExtend={() => void runMicAction('extend')}
                onFinish={() => void runMicAction('finish')}
                onGrab={() => void runMicAction('grab')}
                onRelease={() => void runMicAction('release')}
                onSkip={() => void runMicAction('skip')}
                onSubmitSpeech={submitSpeech}
                session={session}
                submittingSpeech={submittingSpeech}
                viewerMode={viewerMode}
            />
        </Card>
    )
}

async function executeMicAction(action: MicAction, gameId: number, session: ClocktowerMicSessionView) {
    if (action === 'grab') {
        return grabClocktowerMic(gameId)
    }
    if (action === 'release') {
        return releaseClocktowerMic(gameId)
    }
    if (action === 'extend') {
        return extendClocktowerMicSession(gameId, 120)
    }
    if (action === 'finish' && session.currentTurnId) {
        return finishClocktowerMicTurn(gameId, session.currentTurnId)
    }
    if (action === 'skip' && session.currentTurnId) {
        return skipClocktowerMicTurn(gameId, session.currentTurnId)
    }
    return session
}

export function PublicMicPanelContent({
    actionLoading,
    myGameSeatId,
    now,
    onExtend,
    onFinish,
    onGrab,
    onRelease,
    onSkip,
    onSubmitSpeech,
    session,
    submittingSpeech,
    viewerMode,
}: {
    actionLoading: MicAction | null
    gameId: number
    myGameSeatId?: number | null
    viewerMode: ClocktowerViewerMode
    session?: ClocktowerMicSessionView | null
    now: Date
    submittingSpeech: boolean
    onGrab: () => void
    onFinish: () => void
    onRelease: () => void
    onSkip: () => void
    onExtend: () => void
    onSubmitSpeech: (content: string) => Promise<void> | void
}) {
    const holder = currentMicTurn(session)
    const holderSelf = isMicHolder(session, myGameSeatId)
    const canGrab = canGrabClocktowerMic(session, viewerMode, myGameSeatId, now)
    const canFinish = holderSelf && Boolean(session?.currentTurnId)
    const canStorytellerOperate = viewerMode === 'STORYTELLER' && Boolean(session?.currentTurnId)
    const canExtend = viewerMode === 'STORYTELLER' && session?.status === 'GRAB_MIC'
    const grabRemaining = remainingUntil(session?.grabEndsAt, now)
    const turnRemaining = remainingUntil(holder?.expiresAt, now)

    if (!session) {
        return (
            <Space orientation="vertical" style={{width: '100%'}}>
                <Empty description="当前没有公聊麦序"/>
                <PublicSpeechComposer
                    disabled
                    loading={submittingSpeech}
                    onSubmit={onSubmitSpeech}
                    placeholder="当前没有公聊麦序"
                />
            </Space>
        )
    }

    return (
        <Space orientation="vertical" size="middle" style={{width: '100%'}}>
            <Flex align="center" justify="space-between" gap="middle" wrap>
                <Space wrap>
                    <Tag color={session.status === 'GRAB_MIC' ? 'warning' : session.status === 'CLOSED' ? 'default' : 'processing'}>
                        {micStatusLabel(session.status)}
                    </Tag>
                    {holder ? (
                        <Typography.Text>
                            当前发言人：#{holder.seatNo ?? holder.gameSeatId} {holder.displayName ?? holder.gameSeatId}
                        </Typography.Text>
                    ) : (
                        <Typography.Text type="secondary">当前无人持麦</Typography.Text>
                    )}
                    {holder?.actorType === 'AGENT' && <Tag color="purple">Agent</Tag>}
                </Space>
                <Space wrap>
                    <Tag icon={<ClockCircleOutlined/>}>发言 {formatMicRemaining(turnRemaining)}</Tag>
                    <Tag icon={<ClockCircleOutlined/>}>抢麦 {formatMicRemaining(grabRemaining)}</Tag>
                </Space>
            </Flex>
            <Space wrap>
                <Button disabled={!canFinish} icon={<StepForwardOutlined/>} loading={actionLoading === 'finish'} onClick={onFinish}>
                    说完了
                </Button>
                <Button disabled={!canGrab} icon={<AudioOutlined/>} loading={actionLoading === 'grab'} onClick={onGrab} type={canGrab ? 'primary' : 'default'}>
                    抢麦
                </Button>
                <Button disabled={!canFinish} loading={actionLoading === 'release'} onClick={onRelease}>释放麦</Button>
                <Button disabled={!canStorytellerOperate} loading={actionLoading === 'skip'} onClick={onSkip}>ST 跳过</Button>
                <Button disabled={!canExtend} loading={actionLoading === 'extend'} onClick={onExtend}>ST 延长 2 分钟</Button>
            </Space>
            <PublicSpeechComposer
                disabled={!holderSelf}
                loading={submittingSpeech}
                onSubmit={onSubmitSpeech}
                placeholder={speechPlaceholder(session, myGameSeatId)}
            />
            <List
                dataSource={[...session.turns].sort((left, right) => left.turnOrder - right.turnOrder)}
                renderItem={(turn) => (
                    <List.Item>
                        <Space wrap>
                            <Tag>#{turn.seatNo ?? turn.gameSeatId}</Tag>
                            <Typography.Text>{turn.displayName ?? turn.gameSeatId}</Typography.Text>
                            {turn.actorType === 'AGENT' && <Tag color="purple">Agent</Tag>}
                            <Tag>{turn.stage}</Tag>
                            <Tag>{turn.status}</Tag>
                        </Space>
                    </List.Item>
                )}
            />
        </Space>
    )
}

function PublicSpeechComposer({
    disabled,
    loading,
    onSubmit,
    placeholder,
}: {
    disabled: boolean
    loading: boolean
    placeholder: string
    onSubmit: (content: string) => Promise<void> | void
}) {
    const [content, setContent] = useState('')
    const trimmed = content.trim()
    return (
        <Space orientation="vertical" style={{width: '100%'}}>
            <Input.TextArea
                disabled={disabled}
                maxLength={SPEECH_MAX_LENGTH}
                onChange={(event) => setContent(event.target.value)}
                placeholder={placeholder}
                showCount
                style={{minHeight: 72}}
                value={content}
            />
            <Button
                disabled={disabled || trimmed.length === 0}
                loading={loading}
                onClick={() => {
                    void onSubmit(trimmed)
                    setContent('')
                }}
                type="primary"
            >
                发送
            </Button>
        </Space>
    )
}

export function currentMicTurn(session?: ClocktowerMicSessionView | null) {
    if (!session?.currentTurnId) {
        return undefined
    }
    return session.turns.find((turn) => turn.turnId === session.currentTurnId)
}

export function isMicHolder(session: ClocktowerMicSessionView | null | undefined, myGameSeatId?: number | null) {
    return typeof myGameSeatId === 'number' && session?.currentHolderGameSeatId === myGameSeatId
}

export function canGrabClocktowerMic(
    session: ClocktowerMicSessionView | null | undefined,
    viewerMode: ClocktowerViewerMode,
    myGameSeatId: number | null | undefined,
    now: Date,
) {
    return viewerMode === 'PLAYER'
        && typeof myGameSeatId === 'number'
        && session?.status === 'GRAB_MIC'
        && !session.currentHolderGameSeatId
        && remainingUntil(session.grabEndsAt, now) > 0
}

export function formatMicRemaining(seconds: number) {
    const safe = Math.max(0, Math.floor(seconds))
    const minutes = Math.floor(safe / 60).toString().padStart(2, '0')
    const rest = (safe % 60).toString().padStart(2, '0')
    return `${minutes}:${rest}`
}

function remainingUntil(value: string | null | undefined, now: Date) {
    if (!value) {
        return 0
    }
    return Math.max(0, Math.floor((new Date(value).getTime() - now.getTime()) / 1000))
}

function speechPlaceholder(session: ClocktowerMicSessionView, myGameSeatId?: number | null) {
    if (isMicHolder(session, myGameSeatId)) {
        return '当前你持麦，可以公开发言'
    }
    if (session.status === 'GRAB_MIC' && !session.currentHolderGameSeatId) {
        return '抢麦后发言'
    }
    if (session.status === 'CLOSED') {
        return '当前没有公聊麦序'
    }
    return '等待麦序'
}

function micStatusLabel(status: string) {
    if (status === 'ROUND_ROBIN') {
        return '轮流麦'
    }
    if (status === 'GRAB_MIC') {
        return '抢麦'
    }
    if (status === 'CLOSED') {
        return '已结束'
    }
    return status
}
