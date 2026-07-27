import {DatabaseOutlined, DeleteOutlined, EditOutlined, PlusOutlined, TeamOutlined} from '@ant-design/icons'
import {App, Button, Checkbox, Col, Form, Input, InputNumber, Row, Select, Space, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useState} from 'react'
import {DataTable} from '../../components/DataTable'
import {EmptyState} from '../../components/EmptyState'
import {FormDrawer} from '../../components/FormDrawer'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
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

type KnowledgeBaseTableColumnsOptions = {
    canEdit: boolean
    canGrantUsers: boolean
    canDelete: boolean
    onEdit: (record: KnowledgeBaseResponse) => void
    onGrantUsers: (record: KnowledgeBaseResponse) => void | Promise<void>
    onDelete: (id: number) => void | Promise<void>
}

const searchModeOptions = [
    {label: '向量检索', value: 'VECTOR'},
    {label: '关键词检索', value: 'KEYWORD'},
    {label: '混合检索', value: 'HYBRID'},
    {label: '混合重排', value: 'HYBRID_RERANK'},
]

export const knowledgeBaseTableScrollX = 1400

export function knowledgeBaseTableColumns(options: KnowledgeBaseTableColumnsOptions): ColumnsType<KnowledgeBaseResponse> {
    const {canEdit, canGrantUsers, canDelete, onEdit, onGrantUsers, onDelete} = options
    return [
        {
            title: '知识库',
            dataIndex: 'name',
            fixed: 'left',
            width: 240,
            render: (_, record) => <StackedCell primary={record.name} secondary={record.code}/>,
        },
        {
            title: '检索模式',
            dataIndex: 'defaultSearchMode',
            width: 190,
            render: (_, record) => (
                <Space size={4} wrap>
                    <Tag color="blue">{record.defaultSearchMode}</Tag>
                    <Tag color={record.rerankEnabled ? 'purple' : 'default'}>
                        Rerank {record.rerankEnabled ? '开启' : '关闭'}
                    </Tag>
                </Space>
            ),
        },
        {
            title: '默认参数',
            dataIndex: 'defaultTopK',
            width: 150,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={`TopK ${record.defaultTopK}`}
                    secondary={`阈值 ${record.defaultSimilarityThreshold}`}
                />
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (value) => <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value}</Tag>,
        },
        {
            title: '描述',
            dataIndex: 'description',
            width: 260,
            ellipsis: true,
            render: (_, record) => record.description || '-',
        },
        {
            title: '操作',
            fixed: 'right',
            width: 180,
            render: (_, record) => (
                <RowActions
                    actions={[
                        {
                            key: 'edit',
                            label: '编辑',
                            icon: <EditOutlined/>,
                            hidden: !canEdit,
                            onClick: () => onEdit(record),
                        },
                        {
                            key: 'users',
                            label: '用户授权',
                            icon: <TeamOutlined/>,
                            hidden: !canGrantUsers,
                            onClick: () => void onGrantUsers(record),
                        },
                        {
                            key: 'delete',
                            label: '删除',
                            icon: <DeleteOutlined/>,
                            danger: true,
                            hidden: !canDelete,
                            confirm: '确认删除该知识库？其中的文档和切片会一并失效。',
                            onClick: () => void onDelete(record.id),
                        },
                    ]}
                />
            ),
        },
    ]
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
    const canEditRetrievalConfig = canUseRbacButton(auth, ragButtonCodes.kb.retrievalConfig)
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
            defaultSearchMode: 'HYBRID',
            rerankEnabled: false,
            vectorWeight: 0.65,
            keywordWeight: 0.35,
            candidateTopK: 50,
            contextTopK: 6,
            chunkSize: 800,
            chunkOverlap: 120,
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

    const columns = knowledgeBaseTableColumns({
        canEdit,
        canGrantUsers,
        canDelete,
        onEdit: openEditor,
        onGrantUsers: openGrants,
        onDelete: remove,
    })

    return (
        <>
            <PageToolbar
                actions={canCreate &&
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建知识库</Button>}
                description="维护 RAG 知识库、默认检索参数和用户级数据权限。"
                icon={<DatabaseOutlined/>}
                title="知识库管理"
            />
            <DataTable<KnowledgeBaseResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyAction={canCreate && (
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建知识库</Button>
                )}
                emptyDescription="先建一个知识库，之后上传的文档才有地方入库。"
                emptyTitle="还没有知识库"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: knowledgeBaseTableScrollX}}
                title="知识库列表"
            />
            <FormDrawer
                description={editing ? editing.code : '编码创建后不可修改'}
                formId="rag-knowledge-base-form"
                loading={saving}
                onClose={() => setEditorOpen(false)}
                open={editorOpen}
                size="lg"
                title={editing ? '编辑知识库' : '新建知识库'}
            >
                <Form form={form} id="rag-knowledge-base-form" layout="vertical" onFinish={voidify(saveEditor)}>
                    <Form.Item label="名称" name="name" rules={[{required: true, message: '请输入名称'}]}>
                        <Input prefix={<DatabaseOutlined/>}/>
                    </Form.Item>
                    <Form.Item label="编码" name="code" rules={[{required: true, message: '请输入编码'}]}>
                        <Input disabled={Boolean(editing)} placeholder="spring-ai-docs"/>
                    </Form.Item>
                    <Form.Item label="描述" name="description">
                        <Input.TextArea rows={3}/>
                    </Form.Item>
                    <Row gutter={16}>
                        <Col span={8}>
                            <Form.Item label="默认 TopK" name="defaultTopK">
                                <InputNumber className="u-full-width" max={20} min={1}/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item label="默认阈值" name="defaultSimilarityThreshold">
                                <InputNumber className="u-full-width" max={1} min={0} step={0.01}/>
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item label="状态" name="status">
                                <Select options={[
                                    {label: '启用', value: 'ENABLED'},
                                    {label: '禁用', value: 'DISABLED'},
                                ]}/>
                            </Form.Item>
                        </Col>
                    </Row>
                    {canEditRetrievalConfig && (
                        <>
                            <Row gutter={16}>
                                <Col span={12}>
                                    <Form.Item label="检索模式" name="defaultSearchMode">
                                        <Select options={searchModeOptions}/>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item label="Rerank" name="rerankEnabled" valuePropName="checked">
                                        <Checkbox>默认开启</Checkbox>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item label="候选 TopK" name="candidateTopK">
                                        <InputNumber className="u-full-width" max={100} min={1}/>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item label="上下文 TopK" name="contextTopK">
                                        <InputNumber className="u-full-width" max={20} min={1}/>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item label="向量权重" name="vectorWeight">
                                        <InputNumber className="u-full-width" max={1} min={0} step={0.05}/>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item label="关键词权重" name="keywordWeight">
                                        <InputNumber className="u-full-width" max={1} min={0} step={0.05}/>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item label="切片长度" name="chunkSize">
                                        <InputNumber className="u-full-width" max={4000} min={100}/>
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item label="重叠长度" name="chunkOverlap">
                                        <InputNumber className="u-full-width" max={1000} min={0}/>
                                    </Form.Item>
                                </Col>
                            </Row>
                        </>
                    )}
                </Form>
            </FormDrawer>
            <FormDrawer
                description={granting?.code}
                formId="rag-knowledge-base-grant-form"
                loading={saving}
                onClose={() => setGranting(null)}
                open={Boolean(granting)}
                submitText="保存授权"
                title={`用户授权：${granting?.name ?? ''}`}
            >
                <Form form={grantForm} id="rag-knowledge-base-grant-form" layout="vertical"
                      onFinish={voidify(saveGrants)}>
                    <Form.List name="users">
                        {(fields, {add, remove: removeGrant}) => (
                            <Space className="u-full-width" direction="vertical">
                                {fields.map((field) => (
                                    <Row align="bottom" gutter={8} key={field.key}>
                                        <Col span={9}>
                                            <Form.Item {...field} label="用户 ID" name={[field.name, 'userId']}
                                                       rules={[{required: true, message: '请输入用户 ID'}]}>
                                                <InputNumber className="u-full-width" min={1}/>
                                            </Form.Item>
                                        </Col>
                                        <Col span={10}>
                                            <Form.Item {...field} label="权限" name={[field.name, 'accessLevel']}
                                                       rules={[{required: true, message: '请选择权限'}]}>
                                                <Select options={[
                                                    {label: 'READ', value: 'READ'},
                                                    {label: 'WRITE', value: 'WRITE'},
                                                    {label: 'MANAGE', value: 'MANAGE'},
                                                ]}/>
                                            </Form.Item>
                                        </Col>
                                        <Col span={5}>
                                            <Form.Item>
                                                <Button block danger icon={<DeleteOutlined/>}
                                                        onClick={() => removeGrant(field.name)}>
                                                    移除
                                                </Button>
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                ))}
                                {fields.length === 0 && grants.length === 0 && (
                                    <EmptyState
                                        description="该知识库目前只有管理员可见，添加用户后他们才能检索其中的文档。"
                                        inline
                                        title="暂无授权用户"
                                    />
                                )}
                                <Button block icon={<PlusOutlined/>} onClick={() => add({accessLevel: 'READ'})}
                                        type="dashed">
                                    添加用户
                                </Button>
                            </Space>
                        )}
                    </Form.List>
                </Form>
            </FormDrawer>
        </>
    )
}

export const Component = KnowledgeBaseListPage
