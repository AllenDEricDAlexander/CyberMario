import {Alert, Button, Drawer, Form, Input, Space} from 'antd'
import {useEffect, useState} from 'react'

type WorkspaceCreateDrawerProps = {
    open: boolean
    creating?: boolean
    onClose: () => void
    onCreate: (name: string) => Promise<unknown>
}

export function WorkspaceCreateDrawer({open, creating, onClose, onCreate}: WorkspaceCreateDrawerProps) {
    const [form] = Form.useForm<{name: string}>()
    const [error, setError] = useState<string>()

    useEffect(() => {
        if (!open) {
            form.resetFields()
            setError(undefined)
        }
    }, [form, open])

    async function submit() {
        const values = await form.validateFields()
        setError(undefined)
        try {
            await onCreate(values.name)
            form.resetFields()
            onClose()
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : '工作区创建失败')
        }
    }

    return (
        <Drawer
            destroyOnHidden
            extra={(
                <Space>
                    <Button disabled={creating} onClick={onClose}>取消</Button>
                    <Button loading={creating} onClick={() => void submit()} type="primary">创建</Button>
                </Space>
            )}
            onClose={onClose}
            open={open}
            size="default"
            title="创建投资工作区"
        >
            {error && <Alert className="investment-create-error" description={error} showIcon type="error"/>}
            <Form form={form} layout="vertical">
                <Form.Item
                    label="工作区名称"
                    name="name"
                    rules={[
                        {required: true, whitespace: true, message: '请输入工作区名称'},
                        {max: 128, message: '工作区名称不能超过 128 个字符'},
                    ]}
                >
                    <Input autoComplete="off" placeholder="例如：合约研究"/>
                </Form.Item>
            </Form>
        </Drawer>
    )
}
