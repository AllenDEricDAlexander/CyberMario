import {DeleteOutlined, EditOutlined, PlusOutlined, SafetyOutlined, StopOutlined} from '@ant-design/icons'
import {App, Button, Form, Input, Select} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {DataTable} from '../../../components/DataTable'
import {FilterBar} from '../../../components/FilterBar'
import {PageToolbar} from '../../../components/PageToolbar'
import {PermissionTypeTag} from '../../../components/PermissionTypeTag'
import {RowActions, type RowAction} from '../../../components/RowActions'
import {StackedCell} from '../../../components/StackedCell'
import {StatusTag} from '../../../components/StatusTag'
import {usePageData} from '../../../hooks/usePageData'
import {voidify} from '../../../utils/async'
import {enumEquals} from '../../../utils/enum'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {PERMISSION_STATUS_OPTIONS, PERMISSION_TYPE_OPTIONS} from '../rbacEnums'
import {rbacButtonCodes} from '../rbacPermissionCodes'
import {
    createPermission,
    deletePermission,
    getMenuTree,
    getPermissions,
    updatePermission,
    updatePermissionStatus,
} from '../rbacService'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from './PermissionEditorDrawer'

type PermissionFilters = {
    keyword?: string
    permType?: string
    status?: string
}

function PermissionListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [saving, setSaving] = useState(false)
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [editingPermission, setEditingPermission] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [filters, setFilters] = useState<PermissionFilters>({})
    const loadPermissionsPage = useCallback(
        (request: { page: number; size: number }) => getPermissions(request),
        [],
    )
    const {loading, records: permissions, page, size, total, load} = usePageData<PermissionResponse>(
        loadPermissionsPage,
        {initialSize: 50},
    )

    useEffect(() => {
        void getMenuTree().then(setMenus)
    }, [])

    const apiPermissions = useMemo(
        () => permissions.filter((permission) => enumEquals(permission.permType, 3) || enumEquals(permission.permType, 'API')),
        [permissions],
    )
    /** Filtering runs client-side over the loaded page — the list API has no query params. */
    const visiblePermissions = useMemo(() => filterPermissions(permissions, filters), [filters, permissions])
    const canCreate = canUseRbacButton(auth, rbacButtonCodes.permission.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.permission.edit)
    const canChangeStatus = canUseRbacButton(auth, rbacButtonCodes.permission.status)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.permission.delete)

    const columns: ColumnsType<PermissionResponse> = [
        {
            title: '权限',
            dataIndex: 'permCode',
            fixed: 'left',
            width: 260,
            render: (_, record) => <StackedCell primary={record.permName} secondary={record.permCode}/>,
        },
        {
            title: '类型',
            dataIndex: 'permType',
            width: 100,
            render: (_, record) => <PermissionTypeTag value={record.permType}/>,
        },
        {title: '状态', dataIndex: 'status', width: 100, render: (_, record) => <StatusTag value={record.status}/>},
        {
            title: '父权限 / 排序',
            dataIndex: 'parentId',
            width: 130,
            render: (_, record) => (
                <StackedCell plain primary={record.parentId ? `#${record.parentId}` : '顶层权限'}
                             secondary={`排序 ${record.sortNo}`}/>
            ),
        },
        {title: '描述', dataIndex: 'description', render: (_, record) => record.description || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 170,
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
                key: 'status',
                label: isEnabled(record) ? '禁用' : '启用',
                icon: <StopOutlined/>,
                hidden: !canChangeStatus,
                confirm: `确认${isEnabled(record) ? '禁用' : '启用'}该权限？`,
                onClick: () => void toggleStatus(record),
            },
            {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined/>,
                danger: true,
                hidden: !canDelete,
                confirm: '确认删除该权限？删除后无法恢复。',
                onClick: () => void remove(record.id),
            },
        ]
        return <RowActions actions={actions} maxInline={2}/>
    }

    function openEditor(permission?: PermissionResponse) {
        setEditingPermission(permission ?? null)
        setEditorOpen(true)
    }

    async function handleSubmit(request: PermissionRequest) {
        setSaving(true)
        try {
            if (editingPermission) {
                await updatePermission(editingPermission.id, request)
            } else {
                await createPermission(request)
            }
            message.success('保存成功')
            setEditorOpen(false)
            await load()
        } finally {
            setSaving(false)
        }
    }

    async function toggleStatus(permission: PermissionResponse) {
        await updatePermissionStatus(permission.id, isEnabled(permission) ? 'DISABLED' : 'ENABLED')
        message.success('状态已更新')
        await load()
    }

    async function remove(id: number) {
        await deletePermission(id)
        message.success('权限已删除')
        await load()
    }

    return (
        <>
            <PageToolbar
                actions={canCreate && (
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建权限</Button>
                )}
                description="统一维护菜单、按钮和 API 权限。"
                icon={<SafetyOutlined/>}
                title="权限管理"
            />
            <FilterBar<PermissionFilters>
                onReset={() => setFilters({})}
                onSearch={setFilters}
            >
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="权限编码、名称、描述"/>
                </Form.Item>
                <Form.Item label="类型" name="permType">
                    <Select allowClear options={PERMISSION_TYPE_OPTIONS} placeholder="全部"/>
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select allowClear options={PERMISSION_STATUS_OPTIONS} placeholder="全部"/>
                </Form.Item>
            </FilterBar>
            <DataTable<PermissionResponse>
                columns={columns}
                count={total}
                dataSource={visiblePermissions}
                emptyDescription="调整筛选条件，或新建一条菜单、按钮或 API 权限。"
                emptyTitle="没有匹配的权限"
                loading={loading}
                pagination={{current: page, pageSize: size, total, onChange: voidify(load)}}
                rowKey="id"
                scroll={{x: 1040}}
                title="权限列表"
            />
            <PermissionEditorDrawer
                apiPermissions={apiPermissions}
                loading={saving}
                menus={menus}
                onClose={() => setEditorOpen(false)}
                onSubmit={handleSubmit}
                open={editorOpen}
                title={editingPermission ? '编辑权限' : '新建权限'}
                value={editingPermission}
            />
        </>
    )
}

function filterPermissions(permissions: PermissionResponse[], filters: PermissionFilters) {
    const keyword = filters.keyword?.trim().toLowerCase()
    return permissions.filter((permission) => {
        if (filters.permType && permissionTypeOf(permission) !== filters.permType) {
            return false
        }
        if (filters.status && permissionStatusOf(permission) !== filters.status) {
            return false
        }
        if (!keyword) {
            return true
        }
        return [permission.permCode, permission.permName, permission.description]
            .some((field) => field?.toLowerCase().includes(keyword))
    })
}

/**
 * The API returns either the enum name or its numeric code. Normalising the
 * same way `PermissionTypeTag`/`StatusTag` do keeps the filter in step with the
 * badge the row actually shows.
 */
function permissionTypeOf(permission: PermissionResponse) {
    if (enumEquals(permission.permType, 1) || enumEquals(permission.permType, 'MENU')) {
        return 'MENU'
    }
    if (enumEquals(permission.permType, 2) || enumEquals(permission.permType, 'BUTTON')) {
        return 'BUTTON'
    }
    return 'API'
}

function permissionStatusOf(permission: PermissionResponse) {
    if (isEnabled(permission)) {
        return 'ENABLED'
    }
    if (enumEquals(permission.status, 2) || enumEquals(permission.status, 'DRAFT')) {
        return 'DRAFT'
    }
    return 'DISABLED'
}

function isEnabled(permission: PermissionResponse) {
    return enumEquals(permission.status, 1) || enumEquals(permission.status, 'ENABLED')
}

export const Component = PermissionListPage
