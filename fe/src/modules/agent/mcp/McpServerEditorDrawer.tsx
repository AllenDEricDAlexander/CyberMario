import {Button, Drawer, Form, Input, InputNumber, Select} from 'antd'
import {useEffect, useState} from 'react'
import {voidify} from '../../../utils/async'
import type {CreateMcpServerRequest, McpServerResponse, McpTransportType, UpdateMcpServerRequest,} from './mcpTypes'

type McpServerEditorDrawerProps = {
    open: boolean
    loading?: boolean
    server?: McpServerResponse | null
    onClose: () => void
    onSubmit: (request: CreateMcpServerRequest | UpdateMcpServerRequest) => Promise<void>
}

export type McpServerFormValues = Omit<CreateMcpServerRequest, 'headers'> & {
    headersText?: string
}

type McpServerSubmitOptions = {
    editing: boolean
    initialHeadersText: string
}

const transportOptions: Array<{ label: string; value: McpTransportType }> = [
    {label: 'STREAMABLE_HTTP', value: 'STREAMABLE_HTTP'},
    {label: 'SSE', value: 'SSE'},
]

export function McpServerEditorDrawer({open, loading, server, onClose, onSubmit}: McpServerEditorDrawerProps) {
    const [form] = Form.useForm<McpServerFormValues>()
    const [initialHeadersText, setInitialHeadersText] = useState('')
    const editing = Boolean(server)

    useEffect(() => {
        if (!open) return
        const headersText = formatHeaders(server?.headers)
        setInitialHeadersText(headersText)
        form.setFieldsValue({
            serverCode: server?.serverCode ?? '',
            serverName: server?.serverName ?? '',
            transportType: server?.transportType ?? 'STREAMABLE_HTTP',
            baseUrl: server?.baseUrl ?? '',
            endpoint: server?.endpoint ?? '/mcp',
            headersText,
            connectTimeoutMs: server?.connectTimeoutMs ?? 5000,
            requestTimeoutMs: server?.requestTimeoutMs ?? 30000,
        })
    }, [form, open, server])

    async function handleFinish(values: McpServerFormValues) {
        await onSubmit(toMcpServerSubmitRequest(values, {editing, initialHeadersText}))
        form.resetFields()
    }

    return (
        <Drawer
            destroyOnHidden
            extra={<Button form="mcp-server-editor-form" htmlType="submit" loading={loading}
                           type="primary">保存</Button>}
            onClose={onClose}
            open={open}
            title={editing ? '编辑 MCP 服务' : '新建 MCP 服务'}
            width={560}
        >
            <Form form={form} id="mcp-server-editor-form" layout="vertical" onFinish={voidify(handleFinish)}
                  requiredMark={false}>
                {!editing && (
                    <Form.Item label="服务编码" name="serverCode" rules={[{required: true, message: '请输入服务编码'}]}>
                        <Input placeholder="docs-mcp"/>
                    </Form.Item>
                )}
                <Form.Item label="服务名称" name="serverName" rules={[{required: true, message: '请输入服务名称'}]}>
                    <Input/>
                </Form.Item>
                <Form.Item label="传输协议" name="transportType" rules={[{required: true, message: '请选择传输协议'}]}>
                    <Select options={transportOptions}/>
                </Form.Item>
                <Form.Item label="Base URL" name="baseUrl" rules={[{required: true, message: '请输入 Base URL'}]}>
                    <Input placeholder="https://mcp.example.com"/>
                </Form.Item>
                <Form.Item label="Endpoint" name="endpoint" rules={[{required: true, message: '请输入 Endpoint'}]}>
                    <Input placeholder="/mcp"/>
                </Form.Item>
                <Form.Item
                    extra="每行一个请求头，例如 Authorization: Bearer xxx"
                    label="请求头"
                    name="headersText"
                    rules={[{validator: validateHeadersText}]}
                >
                    <Input.TextArea autoSize={{minRows: 4, maxRows: 8}}/>
                </Form.Item>
                <Form.Item label="连接超时（毫秒）" name="connectTimeoutMs">
                    <InputNumber max={60000} min={1000} style={{width: '100%'}}/>
                </Form.Item>
                <Form.Item label="请求超时（毫秒）" name="requestTimeoutMs">
                    <InputNumber max={120000} min={1000} style={{width: '100%'}}/>
                </Form.Item>
            </Form>
        </Drawer>
    )
}

function formatHeaders(headers?: Record<string, string>) {
    if (!headers) {
        return ''
    }
    return Object.entries(headers)
        .map(([key, value]) => `${key}: ${value}`)
        .join('\n')
}

export function parseHeadersText(value?: string) {
    const headers: Record<string, string> = {}
    for (const line of value?.split('\n') ?? []) {
        const trimmed = line.trim()
        if (!trimmed) continue
        const colonIndex = trimmed.indexOf(':')
        if (colonIndex < 0) continue
        const key = trimmed.slice(0, colonIndex).trim()
        const headerValue = trimmed.slice(colonIndex + 1).trim()
        if (key) {
            headers[key] = headerValue
        }
    }
    return headers
}

export function toMcpServerSubmitRequest(
    values: McpServerFormValues,
    {editing, initialHeadersText}: McpServerSubmitOptions,
): CreateMcpServerRequest | UpdateMcpServerRequest {
    const request: UpdateMcpServerRequest = {
        serverName: values.serverName,
        transportType: values.transportType,
        baseUrl: values.baseUrl,
        endpoint: values.endpoint,
        connectTimeoutMs: values.connectTimeoutMs,
        requestTimeoutMs: values.requestTimeoutMs,
    }
    const headersText = values.headersText ?? ''
    if (!editing || headersText !== initialHeadersText) {
        request.headers = parseHeadersText(headersText)
    }
    if (editing) {
        return request
    }
    return {
        ...request,
        serverCode: values.serverCode,
    }
}

function validateHeadersText(_: unknown, value?: string) {
    for (const line of value?.split('\n') ?? []) {
        const trimmed = line.trim()
        if (trimmed && !trimmed.includes(':')) {
            return Promise.reject(new Error('请求头格式必须为 Name: Value'))
        }
    }
    return Promise.resolve()
}
