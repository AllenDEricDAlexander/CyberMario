import {Flex, Select, Typography} from 'antd'
import type {InvestmentReportType} from '../types/investmentResearchTypes'

type InvestmentReportFiltersProps = {
    reportType?: InvestmentReportType
    onReportTypeChange: (reportType?: InvestmentReportType) => void
}

export const investmentReportTypeOptions: {label: string; value: InvestmentReportType}[] = [
    {label: '市场概览', value: 'MARKET_OVERVIEW'},
    {label: '合约分析', value: 'INSTRUMENT_ANALYSIS'},
    {label: '策略分析', value: 'STRATEGY_ANALYSIS'},
    {label: '回测报告', value: 'BACKTEST_REPORT'},
    {label: '组合报告', value: 'PORTFOLIO_REPORT'},
    {label: 'Agent 分析', value: 'AGENT_ANALYSIS'},
]

export function InvestmentReportFilters({reportType, onReportTypeChange}: InvestmentReportFiltersProps) {
    return (
        <Flex align="center" gap={12} wrap>
            <Typography.Text>报告类型</Typography.Text>
            <Select
                allowClear
                aria-label="报告类型筛选"
                onChange={onReportTypeChange}
                options={investmentReportTypeOptions}
                placeholder="全部类型"
                style={{minWidth: 180}}
                value={reportType}
            />
        </Flex>
    )
}

export function investmentReportTypeLabel(reportType?: InvestmentReportType) {
    return investmentReportTypeOptions.find(({value}) => value === reportType)?.label ?? '全部类型'
}
