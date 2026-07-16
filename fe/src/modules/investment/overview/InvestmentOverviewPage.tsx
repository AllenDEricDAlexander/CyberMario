import {Alert, Button, Card, Descriptions, Empty, Flex, Space, Statistic, Tag, Typography} from 'antd'
import {useCallback, useEffect, useRef, useState} from 'react'
import {useNavigate} from 'react-router'
import {resolveErrorMessage} from '../../../services/request'
import {InvestmentAsyncState} from '../components/InvestmentAsyncState'
import {InvestmentDecimalText} from '../components/InvestmentDecimalText'
import {useInvestmentWorkspace} from '../hooks/useInvestmentWorkspace'
import {getInvestmentOverview} from '../services/investmentOverviewService'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentAgentOverviewData,
    InvestmentMarketOverviewData,
    InvestmentOverviewResponse,
    InvestmentOverviewSection,
    InvestmentOverviewSectionCode,
    InvestmentPortfolioOverviewData,
    InvestmentQuantOverviewData,
} from '../types/investmentOverviewTypes'

export default function InvestmentOverviewPage() {
    const navigate = useNavigate()
    const {currentWorkspace} = useInvestmentWorkspace()
    const workspaceId = currentWorkspace?.id
    const [overview, setOverview] = useState<InvestmentOverviewResponse>()
    const [loadState, setLoadState] = useState<InvestmentLoadState>('idle')
    const [loadError, setLoadError] = useState<string>()
    const generationRef = useRef(0)

    const load = useCallback(async () => {
        const generation = ++generationRef.current
        setOverview(undefined)
        setLoadError(undefined)
        if (workspaceId === undefined) {
            setLoadState('empty')
            return
        }
        setLoadState('loading')
        try {
            const response = await getInvestmentOverview(workspaceId)
            if (generation === generationRef.current) {
                setOverview(response)
                setLoadState('ready')
            }
        } catch (reason) {
            if (generation === generationRef.current) {
                setLoadState('error')
                setLoadError(resolveErrorMessage(reason))
            }
        }
    }, [workspaceId])

    useEffect(() => {
        void load()
        return () => {
            generationRef.current += 1
        }
    }, [load])

    if (workspaceId === undefined) {
        return <Empty description="请先选择或创建一个私人投资工作区，再查看投资总览"/>
    }

    return (
        <Space orientation="vertical" size={16} style={{width: '100%'}}>
            <Card>
                <Flex align="center" gap={12} justify="space-between" wrap>
                    <div>
                        <Typography.Title level={4}>投资总览</Typography.Title>
                        <Typography.Text type="secondary">
                            一个服务端快照汇总行情、量化、模拟盘与 Agent 状态，不在前端重算业务指标。
                        </Typography.Text>
                    </div>
                    {overview && <Tag color="blue">数据截止：{overview.dataAsOf}</Tag>}
                </Flex>
            </Card>
            <InvestmentAsyncState error={loadError} onRetry={() => void load()} state={loadState}>
                {overview && (
                    <Space orientation="vertical" size={16} style={{width: '100%'}}>
                        <MarketSection
                            onNavigate={() => void navigate('/investment/market')}
                            section={section(overview, 'MARKET')}
                        />
                        <PortfolioSection
                            onNavigate={() => void navigate('/investment/portfolio')}
                            section={section(overview, 'PORTFOLIO')}
                        />
                        <QuantSection
                            onNavigate={() => void navigate('/investment/quant')}
                            section={section(overview, 'QUANT')}
                        />
                        <AgentSection
                            onNavigate={() => void navigate('/investment/agent')}
                            section={section(overview, 'AGENT')}
                        />
                    </Space>
                )}
            </InvestmentAsyncState>
        </Space>
    )
}

function MarketSection({section, onNavigate}: SectionProps) {
    return (
        <OverviewCard action="进入合约行情" onNavigate={onNavigate} section={section} title="平台行情">
            {section.status === 'AVAILABLE' && (() => {
                const data = section.data as InvestmentMarketOverviewData
                return (
                    <Space orientation="vertical" size={12} style={{width: '100%'}}>
                        <Flex gap={28} wrap>
                            <Statistic title="代码接入合约" value={data.subscribedInstrumentCount}/>
                            <Statistic title="新鲜报价" value={data.freshQuoteCount}/>
                            <Statistic title="陈旧或缺失" value={data.staleOrMissingQuoteCount}/>
                            <Statistic title="开放质量问题" value={data.openQualityIssueCount}/>
                        </Flex>
                        {data.staleOrMissingQuoteCount > 0 && (
                            <Alert showIcon title="存在陈旧或缺失行情，分析与交易可能被服务端拒绝" type="warning"/>
                        )}
                    </Space>
                )
            })()}
        </OverviewCard>
    )
}

function PortfolioSection({section, onNavigate}: SectionProps) {
    return (
        <OverviewCard action="进入模拟盘" onNavigate={onNavigate} section={section} title="模拟账户与仓位">
            {section.status === 'AVAILABLE' && (() => {
                const data = section.data as InvestmentPortfolioOverviewData
                return (
                    <Space orientation="vertical" size={12} style={{width: '100%'}}>
                        <Flex gap={28} wrap>
                            <Statistic title="模拟账户" value={data.accountCount}/>
                            <Statistic title="持仓" value={data.positionCount}/>
                            <Statistic title="风险提示" value={data.riskWarningCount}/>
                        </Flex>
                        <Descriptions column={{xs: 1, md: 3}} size="small">
                            <Descriptions.Item label="权益"><InvestmentDecimalText value={data.equity}/></Descriptions.Item>
                            <Descriptions.Item label="可用余额"><InvestmentDecimalText value={data.availableBalance}/></Descriptions.Item>
                            <Descriptions.Item label="未实现盈亏"><InvestmentDecimalText value={data.unrealizedPnl}/></Descriptions.Item>
                            <Descriptions.Item label="总敞口"><InvestmentDecimalText value={data.grossExposure}/></Descriptions.Item>
                            <Descriptions.Item label="最大回撤"><InvestmentDecimalText value={data.maxDrawdown}/></Descriptions.Item>
                        </Descriptions>
                        {data.riskWarningCount > 0 && <Alert showIcon title="模拟账户存在风险提示" type="warning"/>}
                    </Space>
                )
            })()}
        </OverviewCard>
    )
}

function QuantSection({section, onNavigate}: SectionProps) {
    return (
        <OverviewCard action="进入量化回测" onNavigate={onNavigate} section={section} title="量化回测">
            {section.status === 'AVAILABLE' && (() => {
                const data = section.data as InvestmentQuantOverviewData
                if (data.recentBacktests.length === 0) return <Empty description="当前工作区暂无成功回测"/>
                return (
                    <Space orientation="vertical" size={8} style={{width: '100%'}}>
                        {data.recentBacktests.map((run) => (
                            <Descriptions bordered column={{xs: 1, md: 4}} key={run.runId} size="small">
                                <Descriptions.Item label="Run">#{run.runId}</Descriptions.Item>
                                <Descriptions.Item label="总收益"><InvestmentDecimalText value={run.totalReturn}/></Descriptions.Item>
                                <Descriptions.Item label="最大回撤"><InvestmentDecimalText value={run.maxDrawdown}/></Descriptions.Item>
                                <Descriptions.Item label="完成时间">{run.finishedAt}</Descriptions.Item>
                            </Descriptions>
                        ))}
                    </Space>
                )
            })()}
        </OverviewCard>
    )
}

function AgentSection({section, onNavigate}: SectionProps) {
    return (
        <OverviewCard action="进入 Agent 交易" onNavigate={onNavigate} section={section} title="Agent 运行">
            {section.status === 'AVAILABLE' && (() => {
                const data = section.data as InvestmentAgentOverviewData
                if (data.recentRuns.length === 0) return <Empty description="当前工作区暂无成功 Agent 运行"/>
                return (
                    <Space orientation="vertical" size={8} style={{width: '100%'}}>
                        {data.recentRuns.map((run) => (
                            <Descriptions bordered column={{xs: 1, md: 4}} key={run.runId} size="small">
                                <Descriptions.Item label="Run">#{run.runId}</Descriptions.Item>
                                <Descriptions.Item label="类型">{run.runType}</Descriptions.Item>
                                <Descriptions.Item label="决策">{run.action ?? '-'}</Descriptions.Item>
                                <Descriptions.Item label="数据截止">{run.dataAsOf}</Descriptions.Item>
                            </Descriptions>
                        ))}
                    </Space>
                )
            })()}
        </OverviewCard>
    )
}

function OverviewCard({action, children, onNavigate, section, title}: SectionProps & {
    action: string
    children: React.ReactNode
    title: string
}) {
    return (
        <Card
            extra={<Button onClick={onNavigate}>{action}</Button>}
            title={<Space>{title}<Tag>{section.dataAsOf}</Tag></Space>}
        >
            {section.status === 'ERROR' && (
                <Alert description={section.errorCode} showIcon title={`${title}加载失败`} type="error"/>
            )}
            {section.status === 'UNAVAILABLE' && (
                <Alert showIcon title={`${title}暂不可用`} type="info"/>
            )}
            {children}
        </Card>
    )
}

type SectionProps = {
    section: InvestmentOverviewSection
    onNavigate: () => void
}

function section(response: InvestmentOverviewResponse, code: InvestmentOverviewSectionCode) {
    return response.sections.find((value) => value.code === code) ?? {
        code,
        status: 'UNAVAILABLE' as const,
        dataAsOf: response.dataAsOf,
        data: {},
        errorCode: null,
    }
}
