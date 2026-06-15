import {Alert, Button, Drawer, Form, Select, Switch} from 'antd'
import {useEffect} from 'react'
import {voidify} from '../../../utils/async'
import type {McpToolResponse, McpToolRiskLevel, UpdateMcpToolPolicyRequest} from './mcpTypes'

type McpToolPolicyDrawerProps = {
    open: boolean
    loading?: boolean
    tool?: McpToolResponse | null
    onClose: () => void
    onSubmit: (request: UpdateMcpToolPolicyRequest) => Promise<void>
}

const riskLevelOptions: Array<{ label: string; value: McpToolRiskLevel }> = [
    {label: 'LOW', value: 'LOW'},
    {label: 'MEDIUM', value: 'MEDIUM'},
    {label: 'HIGH', value: 'HIGH'},
]

export function McpToolPolicyDrawer({open, loading, tool, onClose, onSubmit}: McpToolPolicyDrawerProps) {
    const [form] = Form.useForm<UpdateMcpToolPolicyRequest>()
    const requireConfirm = Form.useWatch('requireConfirm', form)

    useEffect(() => {
        if (!open) return
        form.setFieldsValue({
            riskLevel: tool?.riskLevel ?? 'LOW',
            readonly: tool?.readonly ?? true,
            requireConfirm: tool?.requireConfirm ?? false,
        })
    }, [form, open, tool])

    async function handleFinish(values: UpdateMcpToolPolicyRequest) {
        await onSubmit(values)
        form.resetFields()
    }

    return (
        <Drawer
            destroyOnHidden
            extra={<Button form="mcp-tool-policy-form" htmlType="submit" loading={loading} type="primary">保存</Button>}
            onClose={onClose}
            open={open}
            title={`工具策略：${tool?.toolKey ?? ''}`}
            width={480}
        >
            <Form form={form} id="mcp-tool-policy-form" layout="vertical" onFinish={voidify(handleFinish)}
                  requiredMark={false}>
                <Form.Item label="风险等级" name="riskLevel" rules={[{required: true, message: '请选择风险等级'}]}>
                    <Select options={riskLevelOptions}/>
                </Form.Item>
                <Form.Item label="只读工具" name="readonly" valuePropName="checked">
                    <Switch/>
                </Form.Item>
                <Form.Item label="需要确认" name="requireConfirm" valuePropName="checked">
                    <Switch/>
                </Form.Item>
                {requireConfirm && (
                    <Alert message="需要确认的工具在第一阶段不会暴露给 ReactAgent。" showIcon type="warning"/>
                )}
            </Form>
        </Drawer>
    )
}
