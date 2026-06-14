import {DatabaseOutlined, EditOutlined, PlusOutlined, TeamOutlined} from '@ant-design/icons'
import {App, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {usePageData} from '../../hooks/usePageData'
import {voidify} from '../../utils/async'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {
    createRagKnowledgeBase,
    deleteRagKnowledgeBase,
    getRagKnowledgeBases,
    getRagKnowledgeBaseUsers,
    replaceRagKnowledgeBaseUsers,
    updateRagKnowledgeBase,
} from './ragService'
import type {KnowledgeBaseResponse, KnowledgeBaseUserResponse} from './ragTypes'

type KnowledgeBaseFormValues = Partial<KnowledgeBaseResponse>

type KnowledgeBaseGrantFormValues = {
    users?: Array<{ userId: number; accessLevel: string }>
}

function KnowledgeBaseListPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const [saving, setSaving] = useState(false)
    const [editing, setEditing] = useState<KnowledgeBaseResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [granting, setGranting] = useState<KnowledgeBaseResponse | null>(null)
    const [grants, setGrants] = useState<KnowledgeBaseUserResponse[]>([])
    const [form] = Form.useForm<KnowledgeBaseFormValues>()
    const [grantForm] = Form.useForm<KnowledgeBaseGrantFormValues>()

    const canCreate = canUseRbacButton(auth, ragButtonCodes.kb.create)
    const canEdit = canUseRbacButton(auth, ragButtonCodes.kb.edit)
    const canDelete = canUseRbacButton(auth, ragButtonCodes.kb.delete)
    const canGrantUsers = canUseRbacButton(auth, ragButtonCodes.kb.users)
    const loadKnowledgeBases = useCallback(
        (request: { page: number; size: number }) => getRagKnowledgeBases(request),
        [],
    )
    const {loading, records, page, size, total, load} = usePageData<KnowledgeBaseResponse>(loadKnowledgeBases)

    function openEditor(record?: KnowledgeBaseResponse) {
        setEditing(record ?? null)
        form.setFieldsValue(record ?? {
            defaultTopK: 6,
            defaultSimilarityThreshold: 0.55,
            status: 'ENABLED',
        })
        setEditorOpen(true)
    }

    async function saveEditor() {
        const values = await form.validateFields()
        setSaving(true)
        try {
            if (editing) {
                await updateRagKnowledgeBase(editing.id, values)
            } else {
                await createRagKnowledgeBase(values)
            }
            message.success('保存成功')
            setEditorOpen(false)
            await load()
        } finally {
            setSaving(false)
        }
    }

    async function remove(id: number) {
        await deleteRagKnowledgeBase(id)
        message.success('知识库已删除')
        await load()
    }

    async function openGrants(record: KnowledgeBaseResponse) {
        setGranting(record)
        const users = await getRagKnowledgeBaseUsers(record.id)
        setGrants(users)
        grantForm.setFieldsValue({users})
    }

    async function saveGrants() {
        if (!granting) return
        const values = await grantForm.validateFields()
        setSaving(true)
        try {
            const users = await replaceRagKnowledgeBaseUsers(granting.id, values.users ?? [])
            setGrants(users)
            message.success('授权已更新')
        } finally {
            setSaving(false)
        }
    }

    const columns: ColumnsType<KnowledgeBaseResponse> = [
        {title: '名称', dataIndex: 'name', width: 180},
        {title: '编码', dataIndex: 'code', width: 180},
        {title: 'TopK', dataIndex: 'defaultTopK', width: 90},
        {title: '阈值', dataIndex: 'defaultSimilarityThreshold', width: 100},
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (value) => <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value}</Tag>,
        },
        {title: '描述', dataIndex: 'description', render: (_, record) => record.description || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 260,
            render: (_, record) => (
                <Space>
                    {canEdit &&
                        <Button icon={<EditOutlined/>} size="small" onClick={() => openEditor(record)}>编辑</Button>}
                    {canGrantUsers &&
                        <Button icon={<TeamOutlined/>} size="small"
                                onClick={() => void openGrants(record)}>用户</Button>}
                    {canDelete && (
                        <Popconfirm title="确认删除该知识库？" onConfirm={() => void remove(record.id)}>
                            <Button danger size="small">删除</Button>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ]

    return (
        <>
            <PageToolbar
                actions={canCreate &&
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建知识库</Button>}
                description="维护 RAG 知识库、默认检索参数和用户级数据权限。"
                title="知识库管理"
            />
            <Table<KnowledgeBaseResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1100}}
            />
            <Modal
                confirmLoading={saving}
                onCancel={() => setEditorOpen(false)}
                onOk={voidify(saveEditor)}
                open={editorOpen}
                title={editing ? '编辑知识库' : '新建知识库'}
            >
                <Form form={form} layout="vertical">
                    <Form.Item label="名称" name="name" rules={[{required: true, message: '请输入名称'}]}>
                        <Input prefix={<DatabaseOutlined/>}/>
                    </Form.Item>
                    <Form.Item label="编码" name="code" rules={[{required: true, message: '请输入编码'}]}>
                        <Input disabled={Boolean(editing)} placeholder="spring-ai-docs"/>
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                    <Space>
                        <Form.Item label="默认 TopK" name="defaultTopK">
                            <InputNumber min={1} max={20}/>
                        </Form.Item>
                        <Form.Item label="默认阈值" name="defaultSimilarityThreshold">
                            <InputNumber min={0} max={1} step={0.01}/>
                        </Form.Item>
                        <Form.Item label="状态" name="status">
                            <Select options={[
                                {label: '启用', value: 'ENABLED'},
                                {label: '禁用', value: 'DISABLED'},
                            ]}/>
                        </Form.Item>
                    </Space>
                </Form>
            </Modal>
            <Modal
                confirmLoading={saving}
                onCancel={() => setGranting(null)}
                onOk={voidify(saveGrants)}
                open={Boolean(granting)}
                title={`用户授权：${granting?.name ?? ''}`}
            >
                <Form form={grantForm} layout="vertical">
                    <Form.List name="users">
                        {(fields, {add, remove}) => (
                            <Space direction="vertical" style={{width: '100%'}}>
                                {fields.map((field) => (
                                    <Space key={field.key} align="baseline">
                                        <Form.Item {...field} label="用户 ID" name={[field.name, 'userId']}
                                                   rules={[{required: true}]}>
                                            <InputNumber min={1}/>
                                        </Form.Item>
                                        <Form.Item {...field} label="权限" name={[field.name, 'accessLevel']}
                                                   rules={[{required: true}]}>
                                            <Select style={{width: 140}} options={[
                                                {label: 'READ', value: 'READ'},
                                                {label: 'WRITE', value: 'WRITE'},
                                                {label: 'MANAGE', value: 'MANAGE'},
                                            ]}/>
                                        </Form.Item>
                                        <Button onClick={() => remove(field.name)}>移除</Button>
                                    </Space>
                                ))}
                                <Button onClick={() => add({accessLevel: 'READ'})}>添加用户</Button>
                                {grants.length === 0 && <Tag>暂无授权</Tag>}
                            </Space>
                        )}
                    </Form.List>
                </Form>
            </Modal>
        </>
    )
}

export const Component = KnowledgeBaseListPage
