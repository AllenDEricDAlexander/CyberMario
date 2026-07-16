import {Alert, Button, Drawer, Form, Input, InputNumber, Space, Typography} from 'antd'
import {useEffect, useState} from 'react'
import type {
    CreateInvestmentPaperAccountRequest,
} from '../types/investmentPortfolioTypes'

type Props = {
    open: boolean
    onClose: () => void
    onCreate: (request: CreateInvestmentPaperAccountRequest) => Promise<unknown>
}

const DEFAULTS: CreateInvestmentPaperAccountRequest = {
    name: '',
    initialEquity: '10000',
    riskProfile: {
        maxLeverage: '10',
        maxOrderNotional: '1000',
        maxPositionNotional: '5000',
        maxGrossExposureNotional: '10000',
        maxOpenPositions: 5,
        maxDailyLossAmount: '500',
        maxDrawdownRatio: '0.2',
        maxOrdersPerHour: 60,
        cooldownSeconds: 0,
        maxMarketDataAgeSeconds: 30,
        maxSlippageBps: '20',
    },
}

export function PaperAccountCreateDrawer({open, onClose, onCreate}: Props) {
    const [form] = Form.useForm<CreateInvestmentPaperAccountRequest>()
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string>()

    useEffect(() => {
        if (open) {
            form.setFieldsValue(DEFAULTS)
            setError(undefined)
        }
    }, [form, open])

    async function submit() {
        if (submitting) return
        const values = await form.validateFields()
        setSubmitting(true)
        setError(undefined)
        try {
            await onCreate({...values, name: values.name.trim()})
            onClose()
        } catch (reason) {
            setError(message(reason, '模拟账户创建失败'))
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <Drawer
            destroyOnHidden
            extra={<Space><Button disabled={submitting} onClick={onClose}>取消</Button>
                <Button loading={submitting} onClick={() => void submit()} type="primary">创建模拟账户</Button></Space>}
            onClose={onClose}
            open={open}
            size="large"
            title="创建 USDT 合约模拟账户"
        >
            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                <Alert description="新账户的手工交易与 Agent 自动交易开关固定为关闭，创建后需显式开启。" showIcon type="info"/>
                {error && <Alert description={error} showIcon title="创建失败" type="error"/>}
                <Form form={form} layout="vertical">
                    <Form.Item label="账户名称" name="name" rules={[{required: true, whitespace: true}, {max: 128}]}>
                        <Input autoComplete="off"/>
                    </Form.Item>
                    <DecimalItem label="初始权益（USDT）" name="initialEquity" min="0.000000000000000001"/>
                    <Typography.Title level={5}>风险限制</Typography.Title>
                    <RiskFields/>
                </Form>
            </Space>
        </Drawer>
    )
}

export function RiskFields() {
    return (
        <>
            <DecimalItem label="最大杠杆（倍）" name={['riskProfile', 'maxLeverage']} min="0.000000000000000001"/>
            <DecimalItem label="单笔最大名义价值（USDT）" name={['riskProfile', 'maxOrderNotional']} min="0.000000000000000001"/>
            <DecimalItem label="单仓最大名义价值（USDT）" name={['riskProfile', 'maxPositionNotional']} min="0.000000000000000001"/>
            <DecimalItem label="总敞口上限（USDT）" name={['riskProfile', 'maxGrossExposureNotional']} min="0.000000000000000001"/>
            <IntegerItem label="最大持仓数（个）" name={['riskProfile', 'maxOpenPositions']} min={1}/>
            <DecimalItem label="单日最大亏损（USDT）" name={['riskProfile', 'maxDailyLossAmount']} min="0"/>
            <DecimalItem label="最大回撤比例（0-1）" name={['riskProfile', 'maxDrawdownRatio']} min="0" max="1"/>
            <IntegerItem label="每小时最大委托数（笔）" name={['riskProfile', 'maxOrdersPerHour']} min={1}/>
            <IntegerItem label="交易冷却时间（秒）" name={['riskProfile', 'cooldownSeconds']} min={0}/>
            <IntegerItem label="行情最大年龄（秒）" name={['riskProfile', 'maxMarketDataAgeSeconds']} min={1}/>
            <DecimalItem label="最大滑点（基点）" name={['riskProfile', 'maxSlippageBps']} min="0"/>
        </>
    )
}

function DecimalItem({label, name, min, max}: {
    label: string
    name: string | (string | number)[]
    min: string
    max?: string
}) {
    return (
        <Form.Item label={label} name={name} rules={[{required: true, message: `请输入${label}`}]}>
            <InputNumber min={min} max={max} stringMode style={{width: '100%'}}/>
        </Form.Item>
    )
}

function IntegerItem({label, name, min}: {
    label: string
    name: (string | number)[]
    min: number
}) {
    return (
        <Form.Item label={label} name={name} rules={[{required: true, message: `请输入${label}`}]}>
            <InputNumber min={min} precision={0} style={{width: '100%'}}/>
        </Form.Item>
    )
}

function message(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}
