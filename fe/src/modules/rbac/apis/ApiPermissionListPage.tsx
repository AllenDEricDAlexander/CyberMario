import {EditOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Popconfirm, Space, Table, Tooltip} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {ApiRiskTag} from '../../../components/ApiRiskTag'
import {PageToolbar} from '../../../components/PageToolbar'
import {StatusTag} from '../../../components/StatusTag'
import {usePageData} from '../../../hooks/usePageData'
import {voidify} from '../../../utils/async'
import {enumDesc} from '../../../utils/enum'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {
    createApiPermission,
    deleteApiPermission,
    getApiPermissions,
    getMenuTree,
    updateApiPermission
} from '../rbacService'
import {rbacButtonCodes} from '../rbacPermissionCodes'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from '../permissions/PermissionEditorDrawer'

function ApiPermissionListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [saving, setSaving] = useState(false)
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [editingApi, setEditingApi] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const loadApiPermissions = useCallback(
        (request: { page: number; size: number }) => getApiPermissions(request),
        [],
    )
    const {loading, records: permissions, page, size, total, load} = usePageData<PermissionResponse>(loadApiPermissions)

    useEffect(() => {
        void getMenuTree().then(setMenus)
    }, [])

    const apiPermissions = useMemo(() => permissions, [permissions])
    const canCreate = canUseRbacButton(auth, rbacButtonCodes.api.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.api.edit)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.api.delete)

    const columns: ColumnsType<PermissionResponse> = [
        {title: '权限编码', dataIndex: 'permCode', width: 240},
        {title: '权限名称', dataIndex: 'permName', width: 180},
        {title: '方法', width: 90, render: (_, record) => record.api?.httpMethod || '-'},
        {title: 'URL 模式', width: 260, render: (_, record) => record.api?.urlPattern || '-'},
        {title: '匹配', width: 110, render: (_, record) => enumDesc(record.api?.matcherType)},
        {title: '公开', width: 80, render: (_, record) => record.api?.publicFlag ? '是' : '否'},
        {title: '风险', width: 90, render: (_, record) => <ApiRiskTag value={record.api?.riskLevel}/>},
        {title: '状态', dataIndex: 'status', width: 100, render: (_, record) => <StatusTag value={record.status}/>},
        {
            title: '操作',
            fixed: 'right',
            width: 170,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: PermissionResponse) {
        const actions: ReactNode[] = []
        if (canEdit) {
            actions.push(<Button icon={<EditOutlined/>} key="edit" size="small"
                                 onClick={() => openEditor(record)}>编辑</Button>)
        }
        if (canDelete) {
            actions.push(
                <Popconfirm key="delete" title="确认删除该 API 权限？" onConfirm={() => void remove(record.id)}>
                    <Button danger size="small">删除</Button>
                </Popconfirm>,
            )
        }
        return actions.length ? <Space>{actions}</Space> : '-'
    }

    function openEditor(api?: PermissionResponse) {
        setEditingApi(api ?? null)
        setEditorOpen(true)
    }

    async function handleSubmit(request: PermissionRequest) {
        setSaving(true)
        try {
            const body = {...request, permType: 'API' as const}
            if (editingApi) {
                await updateApiPermission(editingApi.id, body)
            } else {
                await createApiPermission(body)
            }
            message.success('保存成功')
            setEditorOpen(false)
            await load()
        } finally {
            setSaving(false)
        }
    }

    async function remove(id: number) {
        await deleteApiPermission(id)
        message.success('API 权限已删除')
        await load()
    }

    return (
        <>
            <PageToolbar
                actions={(
                    <Space>
                        <Tooltip title="后端 RBAC1 首版暂不支持 API 扫描">
                            <Button disabled>扫描 API</Button>
                        </Tooltip>
                        {canCreate && (
                            <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建 API
                                权限</Button>
                        )}
                    </Space>
                )}
                description="维护动态 API 授权规则。"
                title="API 权限"
            />
            <Alert
                className="page-alert"
                message="API scan is not included in rbac1 first release，当前页面支持手动维护 API 权限。"
                showIcon
                type="info"
            />
            <Table<PermissionResponse>
                columns={columns}
                dataSource={apiPermissions}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1200}}
            />
            <PermissionEditorDrawer
                apiPermissions={apiPermissions}
                fixedType="API"
                loading={saving}
                menus={menus}
                onClose={() => setEditorOpen(false)}
                onSubmit={handleSubmit}
                open={editorOpen}
                title={editingApi ? '编辑 API 权限' : '新建 API 权限'}
                value={editingApi}
            />
        </>
    )
}

export const Component = ApiPermissionListPage
