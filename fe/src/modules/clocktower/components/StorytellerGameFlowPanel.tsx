import {ReloadOutlined, StepForwardOutlined} from '@ant-design/icons'
import {App, Button, Descriptions, Empty, List, Space, Spin, Tag, Typography} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {reportGlobalError} from '../../../app/globalError'
import {advanceClocktowerGameFlow, getClocktowerGameFlow} from '../clocktowerService'
import type {ClocktowerGameFlowView} from '../clocktowerTypes'

export function StorytellerGameFlowPanel({
    gameId,
    onGameChanged,
}: {
    gameId: number
    onGameChanged?: () => Promise<void>
}) {
    const {message} = App.useApp()
    const [flow, setFlow] = useState<ClocktowerGameFlowView | null>(null)
    const [loading, setLoading] = useState(false)
    const [actionLoading, setActionLoading] = useState(false)

    const refresh = useCallback(async () => {
        setLoading(true)
        try {
            setFlow(await getClocktowerGameFlow(gameId))
        } catch (caught) {
            reportGlobalError(caught)
            setFlow(null)
        } finally {
            setLoading(false)
        }
    }, [gameId])

    useEffect(() => {
        void refresh()
    }, [refresh])

    async function advance() {
        setActionLoading(true)
        try {
            const nextFlow = await advanceAndReloadClocktowerGameFlow(gameId, onGameChanged)
            setFlow(nextFlow)
            message.success('游戏流程已推进')
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setActionLoading(false)
        }
    }

    return (
        <StorytellerGameFlowPanelContent
            actionLoading={actionLoading}
            flow={flow}
            loading={loading}
            onAdvance={() => void advance()}
            onRefresh={() => void refresh()}
        />
    )
}

export async function advanceAndReloadClocktowerGameFlow(
    gameId: number,
    onGameChanged?: () => Promise<void>,
) {
    const result = await advanceClocktowerGameFlow(gameId)
    await onGameChanged?.()
    return result.flow
}

export function StorytellerGameFlowPanelContent({
    actionLoading,
    flow,
    loading,
    onAdvance,
    onRefresh,
}: {
    actionLoading: boolean
    flow: ClocktowerGameFlowView | null
    loading: boolean
    onAdvance: () => void
    onRefresh: () => void
}) {
    return (
        <Spin spinning={loading}>
            <Space orientation="vertical" size="middle" style={{width: '100%'}}>
                <Space wrap>
                    <Button icon={<ReloadOutlined/>} onClick={onRefresh}>刷新</Button>
                    <Button
                        disabled={!flow?.advanceAllowed}
                        icon={<StepForwardOutlined/>}
                        loading={actionLoading}
                        onClick={onAdvance}
                        type="primary"
                    >
                        推进流程
                    </Button>
                </Space>
                {!flow ? (
                    <Empty description={loading ? '正在加载流程' : '暂无流程数据'}/>
                ) : (
                    <>
                        <Descriptions
                            column={2}
                            items={[
                                {key: 'status', label: '状态', children: flow.status},
                                {key: 'phase', label: '当前阶段', children: <Tag color="blue">{flow.phase}</Tag>},
                                {key: 'dayNo', label: '天数', children: `第 ${flow.dayNo} 天`},
                                {key: 'nightNo', label: '夜数', children: `第 ${flow.nightNo} 夜`},
                                {
                                    key: 'nextPhase',
                                    label: '下一阶段',
                                    children: flow.nextPhase ? <Tag color="processing">{flow.nextPhase}</Tag> : '无',
                                },
                                {
                                    key: 'advanceAllowed',
                                    label: '可推进',
                                    children: flow.advanceAllowed ? '是' : '否',
                                },
                            ]}
                            size="small"
                        />
                        <Space orientation="vertical" size="small" style={{width: '100%'}}>
                            <Typography.Text strong>阻塞原因</Typography.Text>
                            {flow.blockingReasons.length === 0 ? (
                                <Typography.Text type="secondary">无</Typography.Text>
                            ) : (
                                <List
                                    dataSource={flow.blockingReasons}
                                    renderItem={(reason) => <List.Item><Tag color="warning">{reason}</Tag></List.Item>}
                                    size="small"
                                />
                            )}
                        </Space>
                        <Space orientation="vertical" size="small" style={{width: '100%'}}>
                            <Typography.Text strong>流程计数</Typography.Text>
                            <List
                                dataSource={Object.entries(flow.counters)}
                                locale={{emptyText: '暂无计数'}}
                                renderItem={([key, value]) => (
                                    <List.Item>
                                        <Typography.Text>{key}：{formatCounterValue(value)}</Typography.Text>
                                    </List.Item>
                                )}
                                size="small"
                            />
                        </Space>
                    </>
                )}
            </Space>
        </Spin>
    )
}

function formatCounterValue(value: unknown) {
    if (value !== null && typeof value === 'object') {
        return JSON.stringify(value)
    }
    return String(value)
}
