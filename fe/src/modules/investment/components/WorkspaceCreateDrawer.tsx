import {Alert, Form, Input} from 'antd'
import {useEffect, useState} from 'react'
import {FormDrawer} from '../../../components/FormDrawer'

type WorkspaceCreateDrawerProps = {
    open: boolean
    creating?: boolean
    onClose: () => void
    onCreate: (name: string) => Promise<unknown>
}

const FORM_ID = 'investment-workspace-create-form'

export function WorkspaceCreateDrawer({open, creating, onClose, onCreate}: WorkspaceCreateDrawerProps) {
    const [form] = Form.useForm<{name: string}>()
    const [error, setError] = useState<string>()

    useEffect(() => {
        if (!open) {
            form.resetFields()
            setError(undefined)
        }
    }, [form, open])

    async function submit(values: {name: string}) {
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
        <FormDrawer
            formId={FORM_ID}
            loading={creating}
            onClose={onClose}
            open={open}
            size="sm"
            submitText="创建"
            title="创建投资工作区"
        >
            {error && <Alert className="investment-create-error" description={error} showIcon type="error"/>}
            <Form form={form} id={FORM_ID} layout="vertical" onFinish={(values) => void submit(values)}>
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
        </FormDrawer>
    )
}
