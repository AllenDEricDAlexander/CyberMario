import {FileAddOutlined, ReloadOutlined} from '@ant-design/icons'
import {App, Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, Upload} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {UploadFile} from 'antd/es/upload/interface'
import {useEffect, useState} from 'react'
import {useNavigate} from 'react-router'
import {PageToolbar} from '../../components/PageToolbar'
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

function DocumentListPage() {
    const auth = useAuth()
    const {message} = App.useApp()
    const navigate = useNavigate()
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [records, setRecords] = useState<RagDocumentResponse[]>([])
    const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseResponse[]>([])
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(20)
    const [total, setTotal] = useState(0)
    const [uploadOpen, setUploadOpen] = useState(false)
    const [textOpen, setTextOpen] = useState(false)
    const [fileList, setFileList] = useState<UploadFile[]>([])
    const [uploadForm] = Form.useForm()
    const [textForm] = Form.useForm()

    const canUpload = canUseRbacButton(auth, ragButtonCodes.doc.upload)
    const canDelete = canUseRbacButton(auth, ragButtonCodes.doc.delete)
    const canReindex = canUseRbacButton(auth, ragButtonCodes.doc.reindex)

    async function load(nextPage = page, nextSize = size) {
        setLoading(true)
        try {
            const [documentPage, kbPage] = await Promise.all([
                getRagDocuments({page: nextPage, size: nextSize}),
                getRagKnowledgeBases({page: 1, size: 200}),
            ])
            setRecords(documentPage.records)
            setKnowledgeBases(kbPage.records)
            setPage(documentPage.page)
            setSize(documentPage.size)
            setTotal(documentPage.total)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        void load(1, size)
        // eslint-disable-next-line react-hooks/exhaustive-deps
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
            width: 260,
            render: (value, record) => <Button type="link"
                                               onClick={() => navigate(`/rag/documents/${record.id}`)}>{value}</Button>
        },
        {title: '知识库', dataIndex: 'knowledgeBaseId', width: 100},
        {title: '类型', dataIndex: 'fileType', width: 90, render: (value) => <Tag>{value}</Tag>},
        {title: '来源', dataIndex: 'sourceType', width: 90, render: (value) => <Tag>{value}</Tag>},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value) => <Tag
                color={value === 'INDEXED' ? 'success' : value === 'FAILED' ? 'error' : 'processing'}>{value}</Tag>,
        },
        {title: '切片', dataIndex: 'chunkCount', width: 90},
        {title: '已入库', dataIndex: 'indexedChunkCount', width: 90},
        {title: '错误', dataIndex: 'errorMessage', render: (value) => value || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 190,
            render: (_, record) => (
                <Space>
                    {canReindex &&
                        <Button icon={<ReloadOutlined/>} size="small" onClick={() => reindex(record.id)}>重建</Button>}
                    {canDelete && (
                        <Popconfirm title="确认删除该文档和切片？" onConfirm={() => remove(record.id)}>
                            <Button danger size="small">删除</Button>
                        </Popconfirm>
                    )}
                </Space>
            ),
        },
    ]

    const kbOptions = knowledgeBases.map((item) => ({label: `${item.name} (${item.code})`, value: item.id}))

    return (
        <>
            <PageToolbar
                actions={canUpload && (
                    <>
                        <Button icon={<FileAddOutlined/>} onClick={() => setTextOpen(true)}>导入文本</Button>
                        <Button icon={<FileAddOutlined/>} onClick={() => setUploadOpen(true)}
                                type="primary">上传文档</Button>
                    </>
                )}
                description="上传 md、txt、pdf、docx，或导入纯文本。相同内容只保存一份物理文件。"
                title="文档管理"
            />
            <Table<RagDocumentResponse>
                columns={columns}
                dataSource={records}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: load}}
                rowKey="id"
                scroll={{x: 1280}}
            />
            <Modal confirmLoading={saving} onCancel={() => setUploadOpen(false)} onOk={submitUpload} open={uploadOpen}
                   title="上传文档">
                <Form form={uploadForm} initialValues={{parseImmediately: true}} layout="vertical">
                    <Form.Item label="知识库" name="knowledgeBaseId"
                               rules={[{required: true, message: '请选择知识库'}]}>
                        <Select options={kbOptions}/>
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
            <Modal confirmLoading={saving} onCancel={() => setTextOpen(false)} onOk={submitText} open={textOpen}
                   title="导入纯文本">
                <Form form={textForm} initialValues={{parseImmediately: true}} layout="vertical">
                    <Form.Item label="知识库" name="knowledgeBaseId"
                               rules={[{required: true, message: '请选择知识库'}]}>
                        <Select options={kbOptions}/>
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

export const Component = DocumentListPage
