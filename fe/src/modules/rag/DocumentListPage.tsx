import {DeleteOutlined, FileAddOutlined, FileTextOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Form, Input, Modal, Select, Tag, Upload} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {UploadFile} from 'antd/es/upload/interface'
import {useCallback, useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {DataTable} from '../../components/DataTable'
import {PageToolbar} from '../../components/PageToolbar'
import {RowActions} from '../../components/RowActions'
import {StackedCell} from '../../components/StackedCell'
import {usePageData} from '../../hooks/usePageData'
import {voidify} from '../../utils/async'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {ragButtonCodes} from './ragPermissionCodes'
import {
    deleteRagDocument,
    getRagDocuments,
    getRagKnowledgeBases,
    importRagText,
    reindexRagDocument,
    uploadRagDocuments,
} from './ragService'
import type {KnowledgeBaseResponse, RagDocumentResponse} from './ragTypes'

type DocumentUploadFormValues = {
    knowledgeBaseId: number
    parseImmediately: boolean
}

type TextImportFormValues = {
    knowledgeBaseId: number
    title: string
    content: string
    parseImmediately: boolean
}

function DocumentListPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const navigate = useNavigate()
    const [saving, setSaving] = useState(false)
    const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
    const [uploadOpen, setUploadOpen] = useState(false)
    const [textOpen, setTextOpen] = useState(false)
    const [fileList, setFileList] = useState<UploadFile[]>([])
    const [uploadForm] = Form.useForm<DocumentUploadFormValues>()
    const [textForm] = Form.useForm<TextImportFormValues>()

    const canUpload = canUseRbacButton(auth, ragButtonCodes.doc.upload)
    const canImportText = canUseRbacButton(auth, ragButtonCodes.doc.importText)
    const canDelete = canUseRbacButton(auth, ragButtonCodes.doc.delete)
    const canReindex = canUseRbacButton(auth, ragButtonCodes.doc.reindex)
    const loadDocuments = useCallback(
        (request: { page: number; size: number }) => getRagDocuments(request),
        [],
    )
    const {loading, records, page, size, total, load} = usePageData<RagDocumentResponse>(loadDocuments)

    useEffect(() => {
        void getRagKnowledgeBases({page: 1, size: 200}).then((result) => {
            setKnowledgeBases(result.records)
        })
    }, [])

    async function submitUpload() {
        const values = await uploadForm.validateFields()
        const files = fileList.map((item) => item.originFileObj).filter(Boolean) as File[]
        if (!files.length) {
            message.warning('请选择文件')
            return
        }
        setSaving(true)
        try {
            await uploadRagDocuments({
                knowledgeBaseId: values.knowledgeBaseId,
                files,
                parseImmediately: values.parseImmediately,
            })
            message.success('上传成功')
            setUploadOpen(false)
            setFileList([])
            await load()
        } finally {
            setSaving(false)
        }
    }

    async function submitText() {
        const values = await textForm.validateFields()
        setSaving(true)
        try {
            await importRagText(values)
            message.success('文本已导入')
            setTextOpen(false)
            await load()
        } finally {
            setSaving(false)
        }
    }

    async function remove(id: number) {
        await deleteRagDocument(id)
        message.success('文档已删除')
        await load()
    }

    async function reindex(id: number) {
        await reindexRagDocument(id)
        message.success('已重建索引')
        await load()
    }

    const columns: ColumnsType<RagDocumentResponse> = [
        {
            title: '文档名',
            dataIndex: 'displayName',
            fixed: 'left',
            width: 280,
            render: (value: RagDocumentResponse['displayName'], record) => (
                <Button onClick={() => void navigate(`/rag/documents/${record.id}`)} type="link">{value}</Button>
            ),
        },
        {
            title: '知识库 / 来源',
            dataIndex: 'knowledgeBaseId',
            width: 160,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={`知识库 ${record.knowledgeBaseId}`}
                    secondary={`${record.sourceType} · ${record.fileType}`}
                />
            ),
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: RagDocumentResponse['status']) => (
                <Tag color={documentStatusColor(value)}>{value}</Tag>
            ),
        },
        {
            title: '切片',
            dataIndex: 'chunkCount',
            width: 130,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={`${record.indexedChunkCount ?? 0} / ${record.chunkCount ?? 0}`}
                    secondary="已入库 / 总数"
                />
            ),
        },
        {
            title: '解析器',
            dataIndex: 'parserType',
            width: 160,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={record.parserType || '-'}
                    secondary={record.embeddingDimension ? `${record.embeddingDimension} 维` : '未向量化'}
                />
            ),
        },
        {title: '错误', dataIndex: 'errorMessage', render: (_, record) => record.errorMessage || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 160,
            render: (_, record) => (
                <RowActions
                    actions={[
                        {
                            key: 'reindex',
                            label: '重建',
                            icon: <ReloadOutlined/>,
                            hidden: !canReindex,
                            onClick: () => void reindex(record.id),
                        },
                        {
                            key: 'delete',
                            label: '删除',
                            icon: <DeleteOutlined/>,
                            danger: true,
                            hidden: !canDelete,
                            confirm: '确认删除该文档和切片？删除后无法恢复。',
                            onClick: () => void remove(record.id),
                        },
                    ]}
                />
            ),
        },
    ]

    const kbOptions = knowledgeBases.map((item) => ({label: `${item.name} (${item.code})`, value: item.id}))

    return (
        <>
            <PageToolbar
                actions={(canUpload || canImportText) && (
                    <>
                        {canImportText && (
                            <Button icon={<FileAddOutlined/>} onClick={() => setTextOpen(true)}>导入文本</Button>
                        )}
                        {canUpload && (
                            <Button icon={<FileAddOutlined/>} onClick={() => setUploadOpen(true)} type="primary">
                                上传文档
                            </Button>
                        )}
                    </>
                )}
                description="上传 md、txt、pdf、docx，或导入纯文本。相同内容只保存一份物理文件。"
                icon={<FileTextOutlined/>}
                title="文档管理"
            />
            <DataTable<RagDocumentResponse>
                columns={columns}
                count={total}
                dataSource={records}
                emptyAction={canUpload && (
                    <Button icon={<FileAddOutlined/>} onClick={() => setUploadOpen(true)} type="primary">
                        上传文档
                    </Button>
                )}
                emptyDescription="上传 md、txt、pdf、docx，或直接导入一段纯文本来建立第一个文档。"
                emptyTitle="还没有文档"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1280}}
                title="文档列表"
            />
            <Modal
                className="form-modal"
                confirmLoading={saving}
                onCancel={() => setUploadOpen(false)}
                onOk={voidify(submitUpload)}
                open={uploadOpen}
                title="上传文档"
            >
                <Form form={uploadForm} initialValues={{parseImmediately: true}} layout="vertical">
                    <Form.Item label="知识库" name="knowledgeBaseId"
                               rules={[{required: true, message: '请选择知识库'}]}>
                        <Select options={kbOptions} placeholder="选择目标知识库"/>
                    </Form.Item>
                    <Form.Item label="上传后立即解析" name="parseImmediately">
                        <Select options={[{label: '是', value: true}, {label: '否', value: false}]}/>
                    </Form.Item>
                    <Upload.Dragger
                        accept=".md,.txt,.pdf,.docx"
                        beforeUpload={() => false}
                        fileList={fileList}
                        multiple
                        onChange={({fileList: nextFileList}) => setFileList(nextFileList)}
                    >
                        <p>点击或拖拽文件到这里上传</p>
                        <p>支持 md、txt、pdf、docx</p>
                    </Upload.Dragger>
                </Form>
            </Modal>
            <Modal
                className="form-modal"
                confirmLoading={saving}
                onCancel={() => setTextOpen(false)}
                onOk={voidify(submitText)}
                open={textOpen}
                title="导入纯文本"
            >
                <Form form={textForm} initialValues={{parseImmediately: true}} layout="vertical">
                    <Form.Item label="知识库" name="knowledgeBaseId"
                               rules={[{required: true, message: '请选择知识库'}]}>
                        <Select options={kbOptions} placeholder="选择目标知识库"/>
                    </Form.Item>
                    <Form.Item label="标题" name="title" rules={[{required: true, message: '请输入标题'}]}>
                        <Input/>
                    </Form.Item>
                    <Form.Item label="内容" name="content" rules={[{required: true, message: '请输入内容'}]}>
                        <Input.TextArea rows={8}/>
                    </Form.Item>
                    <Form.Item label="导入后立即解析" name="parseImmediately">
                        <Select options={[{label: '是', value: true}, {label: '否', value: false}]}/>
                    </Form.Item>
                </Form>
            </Modal>
        </>
    )
}

function documentStatusColor(status: RagDocumentResponse['status']) {
    if (status === 'INDEXED') {
        return 'success'
    }
    if (status === 'FAILED') {
        return 'error'
    }
    return 'processing'
}

export const Component = DocumentListPage
