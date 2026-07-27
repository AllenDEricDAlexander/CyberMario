import {Alert, Form, Input, Modal, Typography} from 'antd'
import type {Rule} from 'antd/es/form'
import {useEffect, useState, type ReactNode} from 'react'
import {resolveErrorMessage} from '../../services/request'

export type PromptField = {
    name: string
    label: string
    type?: 'text' | 'password' | 'textarea'
    placeholder?: string
    rules?: Rule[]
    extra?: ReactNode
    autoFocus?: boolean
}

type PromptModalProps<Values> = {
    open: boolean
    title: ReactNode
    description?: ReactNode
    fields: PromptField[]
    okText?: string
    cancelText?: string
    danger?: boolean
    onCancel: () => void
    onSubmit: (values: Values) => Promise<void> | void
}

/**
 * Replaces `Modal.confirm({content: <Input/>})`.
 *
 * The confirm-dialog trick had no labels, no validation and no error surface —
 * a wrong value only failed after the request came back. This is a real form:
 * fields validate before submit and server errors render inline instead of
 * closing the dialog.
 */
export function PromptModal<Values = Record<string, string>>({
    open,
    title,
    description,
    fields,
    okText = '确认',
    cancelText = '取消',
    danger,
    onCancel,
    onSubmit,
}: PromptModalProps<Values>) {
    const [form] = Form.useForm<Values>()
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState('')

    useEffect(() => {
        if (open) {
            form.resetFields()
            setError('')
        }
    }, [form, open])

    async function handleOk() {
        setError('')
        let values: Values
        try {
            values = await form.validateFields()
        } catch {
            // Field-level messages are already rendered by the form.
            return
        }
        setSubmitting(true)
        try {
            await onSubmit(values)
        } catch (submitError) {
            setError(resolveErrorMessage(submitError))
            return
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <Modal
            cancelText={cancelText}
            className="form-modal"
            confirmLoading={submitting}
            destroyOnHidden
            okButtonProps={{danger}}
            okText={okText}
            onCancel={onCancel}
            onOk={() => void handleOk()}
            open={open}
            title={title}
        >
            {description && (
                <Typography.Paragraph type="secondary">{description}</Typography.Paragraph>
            )}
            {error && <Alert className="page-alert" message={error} showIcon type="error"/>}
            <Form form={form} layout="vertical" onFinish={() => void handleOk()} requiredMark={false}>
                {fields.map((field) => (
                    <Form.Item
                        extra={field.extra}
                        key={field.name}
                        label={field.label}
                        name={field.name}
                        rules={field.rules}
                    >
                        {renderControl(field)}
                    </Form.Item>
                ))}
            </Form>
        </Modal>
    )
}

function renderControl(field: PromptField) {
    if (field.type === 'password') {
        return <Input.Password autoFocus={field.autoFocus} placeholder={field.placeholder}/>
    }
    if (field.type === 'textarea') {
        return <Input.TextArea autoFocus={field.autoFocus} placeholder={field.placeholder} rows={3}/>
    }
    return <Input autoFocus={field.autoFocus} placeholder={field.placeholder}/>
}
