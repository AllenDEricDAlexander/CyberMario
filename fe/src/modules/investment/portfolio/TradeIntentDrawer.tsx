import {Alert, Button, Descriptions, Drawer, Form, Input, InputNumber, List, Select, Space, Switch, Tag} from 'antd'
import {useEffect, useState} from 'react'
import type {
    InvestmentPaperTradeResult,
    SubmitInvestmentPaperTradeRequest,
} from '../types/investmentPortfolioTypes'

type TradeForm = Omit<SubmitInvestmentPaperTradeRequest, 'dataAsOf' | 'expiresAt' | 'idempotencyKey'>

type Props = {
    open: boolean
    onClose: () => void
    onSubmit: (request: SubmitInvestmentPaperTradeRequest) => Promise<InvestmentPaperTradeResult>
}

export function TradeIntentDrawer({open, onClose, onSubmit}: Props) {
    const [form] = Form.useForm<TradeForm>()
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string>()
    const [result, setResult] = useState<InvestmentPaperTradeResult>()
    const orderType = Form.useWatch('orderType', form)

    useEffect(() => {
        if (open) {
            form.setFieldsValue({
                instrumentId: undefined,
                positionAction: 'OPEN', positionSide: 'LONG', orderType: 'MARKET',
                quantity: undefined,
                requestedNotional: undefined,
                leverage: undefined,
                reduceOnly: false, reason: null, limitPrice: null,
            })
            setError(undefined)
            setResult(undefined)
        }
    }, [form, open])

    async function submit() {
        if (submitting) return
        const values = await form.validateFields()
        setSubmitting(true)
        setError(undefined)
        setResult(undefined)
        try {
            setResult(await onSubmit({
                ...values,
                limitPrice: values.orderType === 'LIMIT' ? values.limitPrice : null,
                reason: values.reason?.trim() || null,
                dataAsOf: new Date().toISOString(),
                expiresAt: null,
                idempotencyKey: idempotencyKey(),
            }))
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '模拟委托提交失败')
        } finally {
            setSubmitting(false)
        }
    }

    const failedChecks = result?.riskResults.filter((check) => !check.passed) ?? []
    return (
        <Drawer
            destroyOnHidden
            extra={<Space><Button disabled={submitting} onClick={onClose}>关闭</Button>
                <Button loading={submitting} onClick={() => void submit()} type="primary">提交模拟委托</Button></Space>}
            onClose={onClose}
            open={open}
            size="large"
            title="提交合约模拟委托"
        >
            <Space orientation="vertical" size={16} style={{width: '100%'}}>
                <Alert description="仅进入模拟账户撮合与风控链路，所有资金变化均为虚拟记账。" showIcon type="info"/>
                {error && <Alert description={error} showIcon title="提交失败" type="error"/>}
                {result && <TradeResult result={result}/>}
                {failedChecks.length > 0 && (
                    <List
                        bordered
                        dataSource={failedChecks}
                        header="未通过的风险规则"
                        renderItem={(check) => (
                            <List.Item>
                                <List.Item.Meta
                                    description={`观察值 ${check.observedValue ?? '-'} / 限制 ${check.limitValue ?? '-'}`}
                                    title={<><Tag color="error">{check.ruleCode}</Tag>{check.message}</>}
                                />
                            </List.Item>
                        )}
                    />
                )}
                <Form form={form} layout="vertical">
                    <Form.Item label="内部合约 ID" name="instrumentId" rules={[{required: true}]}>
                        <InputNumber min={1} precision={0} style={{width: '100%'}}/>
                    </Form.Item>
                    <Form.Item label="仓位动作" name="positionAction" rules={[{required: true}]}>
                        <Select options={[{label: '开仓', value: 'OPEN'}, {label: '平仓', value: 'CLOSE'}]}/>
                    </Form.Item>
                    <Form.Item label="仓位方向" name="positionSide" rules={[{required: true}]}>
                        <Select options={[{label: '多仓', value: 'LONG'}, {label: '空仓', value: 'SHORT'}]}/>
                    </Form.Item>
                    <Form.Item label="委托类型" name="orderType" rules={[{required: true}]}>
                        <Select options={[{label: '市价', value: 'MARKET'}, {label: '限价', value: 'LIMIT'}]}/>
                    </Form.Item>
                    <Decimal label="数量" name="quantity" min="0.000000000000000001"/>
                    <Decimal label="请求名义价值（USDT）" name="requestedNotional" min="0.000000000000000001"/>
                    <Decimal label="杠杆（倍）" name="leverage" min="0.000000000000000001"/>
                    {orderType === 'LIMIT' && <Decimal label="限价" name="limitPrice" min="0.000000000000000001"/>}
                    <Form.Item label="仅减仓" name="reduceOnly" valuePropName="checked"><Switch/></Form.Item>
                    <Form.Item label="原因" name="reason"><Input.TextArea maxLength={2000} rows={3}/></Form.Item>
                </Form>
            </Space>
        </Drawer>
    )
}

function TradeResult({result}: {result: InvestmentPaperTradeResult}) {
    if (result.intentStatus === 'REJECTED') {
        return <Alert description={`Intent #${result.intentId} 未创建待撮合委托`} showIcon title="风险校验未通过" type="error"/>
    }
    if (result.fill) {
        return <Alert description={`成交价 ${result.fill.fillPrice}，数量 ${result.fill.quantity}，手续费 ${result.fill.feeAmount}`} showIcon title="模拟委托已成交" type="success"/>
    }
    return <Descriptions bordered column={1} items={[
        {key: 'intent', label: 'Intent', children: `#${result.intentId} / ${result.intentStatus}`},
        {key: 'order', label: '委托', children: result.order ? `#${result.order.orderId} / ${result.order.status}` : '-'},
        {key: 'state', label: '撮合说明', children: '委托已接受，保持待撮合状态直到符合条件的行情到达。'},
    ]}/>
}

function Decimal({label, name, min}: {label: string; name: keyof TradeForm; min: string}) {
    return <Form.Item label={label} name={name} rules={[{required: true}]}>
        <InputNumber min={min} stringMode style={{width: '100%'}}/>
    </Form.Item>
}

function idempotencyKey() {
    return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `paper-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
