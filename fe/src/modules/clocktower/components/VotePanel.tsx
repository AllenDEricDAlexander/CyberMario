import {Button, Empty, Form, Input, Select, Space} from 'antd'
import type {AvailableActionResponse, PublicSeatResponse} from '../clocktowerTypes'

type VotePanelProps = {
    actions: AvailableActionResponse[]
    seats: PublicSeatResponse[]
    loading?: boolean
    onSubmit: (values: VotePanelValues) => Promise<void>
}

export type VotePanelValues = {
    actionType: string
    targetSeatIds?: number[]
    content?: string
}

export function VotePanel({actions, seats, loading, onSubmit}: VotePanelProps) {
    const [form] = Form.useForm<VotePanelValues>()
    const enabledActions = actions.filter((action) => action.enabled)

    if (enabledActions.length === 0) {
        return <Empty description="暂无可用操作"/>
    }

    return (
        <Form form={form} layout="vertical" onFinish={(values) => void onSubmit(values)}>
            <Form.Item label="操作" name="actionType" rules={[{required: true, message: '请选择操作'}]}>
                <Select
                    options={enabledActions.map((action) => ({label: action.label, value: action.actionType}))}
                    placeholder="选择操作"
                />
            </Form.Item>
            <Form.Item label="目标座位" name="targetSeatIds">
                <Select
                    mode="multiple"
                    options={seats.map((seat) => ({label: `${seat.seatNo} ${seat.displayName}`, value: seat.seatId}))}
                    placeholder="可选"
                />
            </Form.Item>
            <Form.Item label="内容" name="content">
                <Input.TextArea rows={3}/>
            </Form.Item>
            <Space>
                <Button htmlType="submit" loading={loading} type="primary">提交操作</Button>
                <Button onClick={() => form.resetFields()}>清空</Button>
            </Space>
        </Form>
    )
}
