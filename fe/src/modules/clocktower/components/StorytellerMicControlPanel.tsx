import {CloseCircleOutlined, ClockCircleOutlined, PlayCircleOutlined, ReloadOutlined, StepForwardOutlined} from '@ant-design/icons'
import {App, Button, Empty, List, Space, Spin, Tag, Typography} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {
    closeClocktowerMicSession,
    extendClocktowerMicSession,
    getClocktowerMicSession,
    skipClocktowerMicTurn,
    startClocktowerDayMic,
} from '../clocktowerService'
import type {ClocktowerMicSessionView, ClocktowerMicTurnView} from '../clocktowerTypes'

type MicControlAction = 'start' | 'skip' | 'extend' | 'close'

export function StorytellerMicControlPanel({gameId, phase}: { gameId: number; phase: string }) {
    const {message} = App.useApp()
    const [session, setSession] = useState<ClocktowerMicSessionView | null>(null)
    const [loading, setLoading] = useState(false)
    const [actionLoading, setActionLoading] = useState<MicControlAction | null>(null)
    const [now, setNow] = useState(() => new Date())

    const refresh = useCallback(async () => {
        if (!isClocktowerDayPhase(phase)) {
            setSession(null)
            setLoading(false)
            return
        }
        setLoading(true)
        try {
            setSession(await loadStorytellerMicSession(gameId, phase))
        } catch (caught) {
            reportGlobalError(caught)
            setSession(null)
        } finally {
            setLoading(false)
        }
    }, [gameId, phase])

    useEffect(() => {
        void refresh()
    }, [refresh])

    useEffect(() => {
        const timer = window.setInterval(() => setNow(new Date()), 1000)
        return () => window.clearInterval(timer)
    }, [])

    async function runAction(action: MicControlAction) {
        if (action === 'start' && !isClocktowerDayPhase(phase)) {
            return
        }
        setActionLoading(action)
        try {
            let next: ClocktowerMicSessionView | null = session
            if (action === 'start') {
                next = await startStorytellerMicSession(gameId, phase)
            }
            if (action === 'skip' && session?.currentTurnId) {
                next = await skipClocktowerMicTurn(gameId, session.currentTurnId)
            }
            if (action === 'extend') {
                next = await extendClocktowerMicSession(gameId, 120)
            }
            if (action === 'close') {
                next = await closeClocktowerMicSession(gameId)
            }
            setSession(next)
            message.success('麦序已更新')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setActionLoading(null)
        }
    }

    return (
        <StorytellerMicControlPanelContent
            actionLoading={actionLoading}
            dayPhase={isClocktowerDayPhase(phase)}
            loading={loading}
            now={now}
            onClose={() => void runAction('close')}
            onExtend={() => void runAction('extend')}
            onRefresh={() => void refresh()}
            onSkip={() => void runAction('skip')}
            onStart={() => void runAction('start')}
            session={session}
        />
    )
}

export function StorytellerMicControlPanelContent({
    actionLoading,
    dayPhase,
    loading,
    now,
    onClose,
    onExtend,
    onRefresh,
    onSkip,
    onStart,
    session,
}: {
    loading: boolean
    actionLoading: MicControlAction | null
    dayPhase: boolean
    session: ClocktowerMicSessionView | null
    now: Date
    onRefresh: () => void
    onStart: () => void
    onSkip: () => void
    onExtend: () => void
    onClose: () => void
}) {
    const holder = currentMicTurn(session)
    const grabRemaining = remainingUntil(session?.grabEndsAt, now)
    const turnRemaining = remainingUntil(holder?.expiresAt, now)
    const canSkip = Boolean(session?.currentTurnId)
    const canExtend = session?.status === 'GRAB_MIC'
    const canClose = Boolean(session && session.status !== 'CLOSED')

    return (
        <Spin spinning={loading}>
            <Space orientation="vertical" size="middle" style={{width: '100%'}}>
                <Space wrap>
                    <Button icon={<ReloadOutlined/>} onClick={onRefresh}>刷新</Button>
                    <Button
                        disabled={!dayPhase}
                        icon={<PlayCircleOutlined/>}
                        loading={actionLoading === 'start'}
                        onClick={onStart}
                        type="primary"
                    >
                        开启白天麦序
                    </Button>
                    <Button disabled={!canSkip} icon={<StepForwardOutlined/>} loading={actionLoading === 'skip'} onClick={onSkip}>
                        跳过当前
                    </Button>
                    <Button disabled={!canExtend} icon={<ClockCircleOutlined/>} loading={actionLoading === 'extend'} onClick={onExtend}>
                        延长 2 分钟
                    </Button>
                    <Button danger disabled={!canClose} icon={<CloseCircleOutlined/>} loading={actionLoading === 'close'} onClick={onClose}>
                        关闭公聊
                    </Button>
                </Space>
                {!session ? (
                    <Empty description={dayPhase ? '当前没有公聊麦序' : '白天阶段可开启公聊麦序'}/>
                ) : (
                    <>
                        <Space wrap>
                            <Tag color={session.status === 'GRAB_MIC' ? 'warning' : session.status === 'CLOSED' ? 'default' : 'processing'}>
                                {micStatusLabel(session.status)}
                            </Tag>
                            <Tag icon={<ClockCircleOutlined/>}>发言 {formatRemaining(turnRemaining)}</Tag>
                            <Tag icon={<ClockCircleOutlined/>}>抢麦 {formatRemaining(grabRemaining)}</Tag>
                        </Space>
                        <Typography.Text>
                            当前持麦：{holder ? `#${holder.seatNo ?? holder.gameSeatId} ${holder.displayName ?? holder.gameSeatId}` : '无人'}
                        </Typography.Text>
                        <List
                            dataSource={[...session.turns].sort((left, right) => left.turnOrder - right.turnOrder)}
                            renderItem={(turn) => (
                                <List.Item>
                                    <Space wrap>
                                        <Typography.Text>#{turn.seatNo ?? turn.gameSeatId} {turn.displayName ?? turn.gameSeatId}</Typography.Text>
                                        {turn.actorType === 'AGENT' && <Tag color="purple">Agent</Tag>}
                                        <Tag>{turn.status}</Tag>
                                        <Tag>{turn.stage}</Tag>
                                    </Space>
                                </List.Item>
                            )}
                        />
                    </>
                )}
            </Space>
        </Spin>
    )
}

export function isClocktowerDayPhase(phase: string) {
    return phase === 'DAY'
}

export function loadStorytellerMicSession(gameId: number, phase: string) {
    if (!isClocktowerDayPhase(phase)) {
        return Promise.resolve(null)
    }
    return getClocktowerMicSession(gameId)
}

export function startStorytellerMicSession(gameId: number, phase: string) {
    if (!isClocktowerDayPhase(phase)) {
        return Promise.resolve(null)
    }
    return startClocktowerDayMic(gameId)
}

function currentMicTurn(session?: ClocktowerMicSessionView | null): ClocktowerMicTurnView | undefined {
    if (!session?.currentTurnId) {
        return undefined
    }
    return session.turns.find((turn) => turn.turnId === session.currentTurnId)
}

function remainingUntil(value: string | null | undefined, now: Date) {
    if (!value) {
        return 0
    }
    return Math.max(0, Math.ceil((new Date(value).getTime() - now.getTime()) / 1000))
}

function formatRemaining(seconds: number) {
    const minutes = Math.floor(seconds / 60).toString().padStart(2, '0')
    const rest = Math.floor(seconds % 60).toString().padStart(2, '0')
    return `${minutes}:${rest}`
}

function micStatusLabel(status: string) {
    if (status === 'ROUND_ROBIN') {
        return '轮流麦'
    }
    if (status === 'GRAB_MIC') {
        return '抢麦'
    }
    if (status === 'CLOSED') {
        return '已关闭'
    }
    return status
}
