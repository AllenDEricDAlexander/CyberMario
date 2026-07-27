import {ApiOutlined, DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons'
import {Alert, App, Button, Form, Input, Select, Space, Tag, Tooltip} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {ApiRiskTag} from '../../../components/ApiRiskTag'
import {DataTable} from '../../../components/DataTable'
import {FilterBar} from '../../../components/FilterBar'
import {PageToolbar} from '../../../components/PageToolbar'
import {RowActions, type RowAction} from '../../../components/RowActions'
import {StackedCell} from '../../../components/StackedCell'
import {StatusTag} from '../../../components/StatusTag'
import {usePageData} from '../../../hooks/usePageData'
import {voidify} from '../../../utils/async'
import {enumDesc, enumEquals} from '../../../utils/enum'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {API_RISK_LEVEL_OPTIONS, HTTP_METHOD_OPTIONS} from '../rbacEnums'
import {rbacButtonCodes} from '../rbacPermissionCodes'
import {
    createApiPermission,
    deleteApiPermission,
    getApiPermissions,
    getMenuTree,
    updateApiPermission
} from '../rbacService'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from '../permissions/PermissionEditorDrawer'

type ApiFilters = {
    keyword?: string
    httpMethod?: string
    riskLevel?: string
}

function ApiPermissionListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [saving, setSaving] = useState(false)
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [editingApi, setEditingApi] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [filters, setFilters] = useState<ApiFilters>({})
    const loadApiPermissions = useCallback(
        (request: { page: number; size: number }) => getApiPermissions(request),
        [],
    )
    const {loading, records: permissions, page, size, total, load} = usePageData<PermissionResponse>(loadApiPermissions)

    useEffect(() => {
        void getMenuTree().then(setMenus)
    }, [])

    const apiPermissions = useMemo(() => permissions, [permissions])
    /** Filtering runs client-side over the loaded page — the list API has no query params. */
    const visiblePermissions = useMemo(() => filterApiPermissions(apiPermissions, filters), [apiPermissions, filters])
    const canCreate = canUseRbacButton(auth, rbacButtonCodes.api.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.api.edit)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.api.delete)

    const columns: ColumnsType<PermissionResponse> = [
        {
            title: '权限',
            dataIndex: 'permCode',
            fixed: 'left',
            width: 260,
            render: (_, record) => <StackedCell primary={record.permName} secondary={record.permCode}/>,
        },
        {
            title: '接口',
            width: 300,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={`${record.api?.httpMethod || '-'} ${record.api?.urlPattern || '-'}`}
                    secondary={`${enumDesc(record.api?.matcherType)} · ${record.api?.publicFlag ? '公开接口' : '需鉴权'}`}
                />
            ),
        },
        {
            title: '风险 / 状态',
            width: 150,
            render: (_, record) => (
                <Space size={4} wrap>
                    <ApiRiskTag value={record.api?.riskLevel}/>
                    <StatusTag value={record.status}/>
                </Space>
            ),
        },
        {
            title: '服务',
            width: 130,
            render: (_, record) => record.api?.serviceTag ? <Tag>{record.api.serviceTag}</Tag> : '-',
        },
        {
            title: '操作',
            fixed: 'right',
            width: 150,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: PermissionResponse) {
        const actions: RowAction[] = [
            {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined/>,
                hidden: !canEdit,
                onClick: () => openEditor(record),
            },
            {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined/>,
                danger: true,
                hidden: !canDelete,
                confirm: '确认删除该 API 权限？删除后无法恢复。',
                onClick: () => void remove(record.id),
            },
        ]
        return <RowActions actions={actions} maxInline={2}/>
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
                            <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">
                                新建 API 权限
                            </Button>
                        )}
                    </Space>
                )}
                description="维护动态 API 授权规则。"
                icon={<ApiOutlined/>}
                title="API 权限"
            />
            <Alert
                className="page-alert"
                message="API scan is not included in rbac1 first release，当前页面支持手动维护 API 权限。"
                showIcon
                type="info"
            />
            <FilterBar<ApiFilters>
                onReset={() => setFilters({})}
                onSearch={setFilters}
            >
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="权限编码、名称、URL、服务标识"/>
                </Form.Item>
                <Form.Item label="请求方法" name="httpMethod">
                    <Select allowClear options={HTTP_METHOD_OPTIONS} placeholder="全部"/>
                </Form.Item>
                <Form.Item label="风险等级" name="riskLevel">
                    <Select allowClear options={API_RISK_LEVEL_OPTIONS} placeholder="全部"/>
                </Form.Item>
            </FilterBar>
            <DataTable<PermissionResponse>
                columns={columns}
                count={total}
                dataSource={visiblePermissions}
                emptyDescription="调整筛选条件，或新建一条 API 权限来描述需要鉴权的接口。"
                emptyTitle="没有匹配的 API 权限"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1030}}
                title="API 权限列表"
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

function filterApiPermissions(permissions: PermissionResponse[], filters: ApiFilters) {
    const keyword = filters.keyword?.trim().toLowerCase()
    return permissions.filter((permission) => {
        if (filters.httpMethod && permission.api?.httpMethod !== filters.httpMethod) {
            return false
        }
        if (filters.riskLevel && riskLevelOf(permission) !== filters.riskLevel) {
            return false
        }
        if (!keyword) {
            return true
        }
        return [
            permission.permCode,
            permission.permName,
            permission.api?.urlPattern,
            permission.api?.serviceTag,
            permission.api?.operationName,
        ].some((field) => field?.toLowerCase().includes(keyword))
    })
}

/** Normalised the same way `ApiRiskTag` renders it, so filter and badge agree. */
function riskLevelOf(permission: PermissionResponse) {
    const value = permission.api?.riskLevel
    if (enumEquals(value, 3) || enumEquals(value, 'HIGH')) {
        return 'HIGH'
    }
    if (enumEquals(value, 2) || enumEquals(value, 'MEDIUM')) {
        return 'MEDIUM'
    }
    return 'LOW'
}

export const Component = ApiPermissionListPage
