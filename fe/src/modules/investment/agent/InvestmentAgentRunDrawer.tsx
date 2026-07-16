import {Alert, Button, Card, Descriptions, Drawer, Flex, Space, Spin, Tag, Typography} from 'antd'
import type {
    InvestmentAgentDecisionResponse,
    InvestmentAgentExecutionResponse,
} from '../types/investmentAgentTypes'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {useAgentRunPolling} from './useAgentRunPolling'

type InvestmentAgentRunDrawerProps = {
    open: boolean
    runId?: number
    onClose: () => void
}

export function InvestmentAgentRunDrawer({open, runId, onClose}: InvestmentAgentRunDrawerProps) {
    const {detail, error, polling, refresh} = useAgentRunPolling(open ? runId : undefined)
    const run = detail?.run

    return (
        <Drawer
            destroyOnHidden
            extra={<Button loading={polling} onClick={refresh}>刷新</Button>}
            onClose={onClose}
            open={open}
            size="large"
            title={runId === undefined ? 'Agent 运行详情' : `Agent 运行 #${runId}`}
        >
            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                <Alert
                    description="Agent 不会连接实盘账户，也不提供逐单确认入口。"
                    title="仅模拟盘，风控通过后自动执行"
                    showIcon
                    type="warning"
                />
                {!detail && !error && <Flex justify="center"><Spin/></Flex>}
                {error && <Alert description={error} showIcon title="Agent 状态刷新失败" type="warning"/>}
                {run && (
                    <Descriptions
                        bordered
                        column={2}
                        items={[
                            {key: 'status', label: '运行状态', children: <Tag color={statusColor(run.status)}>{run.status}</Tag>},
                            {key: 'type', label: '运行类型', children: runTypeLabel(run.runType)},
                            {key: 'preset', label: '固定预设', children: run.presetCode},
                            {key: 'cutoff', label: '数据截止', children: run.dataAsOf},
                            {key: 'account', label: '模拟账户', children: run.accountId ? `#${run.accountId}` : '未绑定'},
                            {key: 'report', label: '分析报告', children: run.reportId ? `#${run.reportId}` : '尚未生成'},
                        ]}
                    />
                )}
                {run?.status === 'FAILED' && (
                    <Alert
                        description={run.errorMessage ?? 'Agent 运行失败'}
                        showIcon
                        title={run.errorCode ?? 'INVESTMENT_AGENT_FAILED'}
                        type="error"
                    />
                )}
                {detail?.decisions.length === 0 && run?.status === 'SUCCEEDED' && (
                    <Alert showIcon title="运行已结束，但没有可展示的决策" type="info"/>
                )}
                {detail?.decisions.map((decision) => (
                    <DecisionCard decision={decision} key={decision.id}/>
                ))}
            </Space>
        </Drawer>
    )
}

function DecisionCard({decision}: {decision: InvestmentAgentDecisionResponse}) {
    return (
        <Card
            title={(
                <Flex align="center" gap={8} wrap>
                    <span>决策 #{decision.id}</span>
                    <Tag color={actionColor(decision.action)}>{actionLabel(decision.action)}</Tag>
                    <Tag>{decision.executionStatus}</Tag>
                </Flex>
            )}
        >
            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                <Descriptions
                    column={2}
                    size="small"
                    items={[
                        {key: 'instrument', label: '合约 ID', children: decision.instrumentId ?? '组合级'},
                        {key: 'confidence', label: '置信度', children: <InvestmentDecimalText value={decision.confidence}/>},
                        {key: 'horizon', label: '分析周期', children: decision.horizon},
                        {key: 'cutoff', label: '数据截止', children: decision.dataAsOf},
                        {key: 'quantity', label: '建议数量', children: <InvestmentDecimalText value={decision.requestedQuantity}/>},
                        {key: 'notional', label: '建议名义价值', children: <InvestmentDecimalText value={decision.requestedNotional}/>},
                        {key: 'leverage', label: '建议杠杆', children: <InvestmentDecimalText value={decision.requestedLeverage}/>},
                        {key: 'order', label: '委托方式', children: decision.orderType ?? '-'},
                    ]}
                />
                <section aria-label="Agent 分析依据">
                    <Typography.Title level={5}>分析依据</Typography.Title>
                    <Typography.Paragraph>{decision.thesis}</Typography.Paragraph>
                </section>
                <Flex gap={24} wrap>
                    <DecisionList items={decision.risks} title="风险"/>
                    <DecisionList items={decision.invalidation} title="失效条件"/>
                </Flex>
                <ExecutionChain decision={decision} execution={decision.execution}/>
            </Space>
        </Card>
    )
}

function DecisionList({items, title}: {items: string[]; title: string}) {
    return (
        <section aria-label={title} style={{minWidth: 240, flex: 1}}>
            <Typography.Title level={5}>{title}</Typography.Title>
            <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
        </section>
    )
}

function ExecutionChain({
    decision,
    execution,
}: {
    decision: InvestmentAgentDecisionResponse
    execution: InvestmentAgentExecutionResponse | null
}) {
    return (
        <section aria-label="决策执行链">
            <Typography.Title level={5}>决策 → 意图 → 风控 → 委托 → 成交</Typography.Title>
            {execution?.intentStatus === 'RISK_REJECTED' && (
                <Alert showIcon title="模拟交易被风控拒绝，未创建委托" type="error"/>
            )}
            <ol>
                <li>决策：{actionLabel(decision.action)} / {decision.status}</li>
                <li>交易意图：{execution ? `#${execution.intentId} / ${execution.intentStatus}` : '未创建'}</li>
                <li>
                    风控：{execution?.riskChecks.length
                        ? execution.riskChecks.map((risk) => `${risk.ruleCode} ${risk.passed ? '通过' : '拒绝'}：${risk.message}`).join('；')
                        : '无风控结果'}
                </li>
                <li>模拟委托：{execution?.order ? `#${execution.order.id} / ${execution.order.status}` : '未创建'}</li>
                <li>
                    模拟成交：{execution?.fill
                        ? `#${execution.fill.id} / ${execution.fill.quantity} @ ${execution.fill.price} / 手续费 ${execution.fill.feeAmount}`
                        : '尚未成交'}
                </li>
            </ol>
        </section>
    )
}

function actionLabel(action: InvestmentAgentDecisionResponse['action']) {
    return ({
        HOLD: '观望',
        OPEN_LONG: '开多',
        OPEN_SHORT: '开空',
        CLOSE: '平仓',
        REDUCE: '减仓',
    } as const)[action]
}

function actionColor(action: InvestmentAgentDecisionResponse['action']) {
    if (action === 'OPEN_LONG') return 'success'
    if (action === 'OPEN_SHORT' || action === 'CLOSE') return 'error'
    if (action === 'REDUCE') return 'warning'
    return 'default'
}

function statusColor(status: string) {
    if (status === 'SUCCEEDED') return 'success'
    if (status === 'FAILED') return 'error'
    if (status === 'RUNNING') return 'processing'
    return 'default'
}

function runTypeLabel(runType: string) {
    return ({
        MARKET_ANALYSIS: '市场分析',
        INSTRUMENT_ANALYSIS: '合约分析',
        STRATEGY_REVIEW: '策略复盘',
        PORTFOLIO_REVIEW: '组合复盘',
        AUTO_TRADE: 'Agent 自动模拟交易',
    } as Record<string, string>)[runType] ?? runType
}
