import {Alert, Form, InputNumber} from 'antd'
import {useEffect, useState} from 'react'
import {FormDrawer} from '../../../components/FormDrawer'
import type {
    InvestmentRiskProfile,
    UpdateInvestmentRiskProfileRequest,
} from '../types/investmentPortfolioTypes'

type Props = {
    open: boolean
    profile?: InvestmentRiskProfile
    onClose: () => void
    onSave: (request: UpdateInvestmentRiskProfileRequest) => Promise<InvestmentRiskProfile>
}

const FORM_ID = 'investment-risk-profile-form'

export function RiskProfileDrawer({open, profile, onClose, onSave}: Props) {
    const [form] = Form.useForm<UpdateInvestmentRiskProfileRequest>()
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState<string>()

    useEffect(() => {
        if (open && profile) {
            form.setFieldsValue(profile)
            setError(undefined)
        }
    }, [form, open, profile])

    async function save(values: UpdateInvestmentRiskProfileRequest) {
        if (saving || !profile) return
        setSaving(true)
        setError(undefined)
        try {
            await onSave({...values, version: profile.version})
            onClose()
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '风险配置保存失败')
        } finally {
            setSaving(false)
        }
    }

    return (
        <FormDrawer
            footerHint="保存会整体替换风险限制，服务端按版本号拒绝过期提交。"
            formId={FORM_ID}
            loading={saving}
            onClose={onClose}
            open={open}
            size="lg"
            submitText="保存风险限制"
            title="模拟账户风险限制"
        >
            {error && <Alert className="page-alert" description={error} showIcon title="保存失败" type="error"/>}
            <Form form={form} id={FORM_ID} layout="vertical" onFinish={(values) => void save(values)}>
                <Decimal label="最大杠杆（倍）" name="maxLeverage" min="0.000000000000000001"/>
                <Decimal label="单笔最大名义价值（USDT）" name="maxOrderNotional" min="0.000000000000000001"/>
                <Decimal label="单仓最大名义价值（USDT）" name="maxPositionNotional" min="0.000000000000000001"/>
                <Decimal label="总敞口上限（USDT）" name="maxGrossExposureNotional" min="0.000000000000000001"/>
                <Integer label="最大持仓数（个）" name="maxOpenPositions" min={1}/>
                <Decimal label="单日最大亏损（USDT）" name="maxDailyLossAmount" min="0"/>
                <Decimal label="最大回撤比例（0-1）" name="maxDrawdownRatio" min="0" max="1"/>
                <Integer label="每小时最大委托数（笔）" name="maxOrdersPerHour" min={1}/>
                <Integer label="交易冷却时间（秒）" name="cooldownSeconds" min={0}/>
                <Integer label="行情最大年龄（秒）" name="maxMarketDataAgeSeconds" min={1}/>
                <Decimal label="最大滑点（基点）" name="maxSlippageBps" min="0"/>
            </Form>
        </FormDrawer>
    )
}

function Decimal({label, name, min, max}: {
    label: string
    name: keyof UpdateInvestmentRiskProfileRequest
    min: string
    max?: string
}) {
    return <Form.Item label={label} name={name} rules={[{required: true}]}>
        <InputNumber className="u-full-width" min={min} max={max} stringMode/>
    </Form.Item>
}

function Integer({label, name, min}: {
    label: string
    name: keyof UpdateInvestmentRiskProfileRequest
    min: number
}) {
    return <Form.Item label={label} name={name} rules={[{required: true}]}>
        <InputNumber className="u-full-width" min={min} precision={0}/>
    </Form.Item>
}
