import {
    BranchesOutlined,
    DeleteOutlined,
    EditOutlined,
    PlusOutlined,
    SafetyOutlined,
    TeamOutlined,
} from '@ant-design/icons'
import {App, Button, Form, Input, Select, Skeleton, Space, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {DataTable} from '../../../components/DataTable'
import {EmptyState} from '../../../components/EmptyState'
import {ErrorState} from '../../../components/ErrorState'
import {FilterBar} from '../../../components/FilterBar'
import {PageToolbar} from '../../../components/PageToolbar'
import {RowActions, type RowAction} from '../../../components/RowActions'
import {StackedCell} from '../../../components/StackedCell'
import {StatusTag} from '../../../components/StatusTag'
import {usePageData} from '../../../hooks/usePageData'
import {resolveErrorMessage} from '../../../services/request'
import {voidify} from '../../../utils/async'
import {enumEquals} from '../../../utils/enum'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {rbacButtonCodes} from '../rbacPermissionCodes'
import {
    createRole,
    deleteRole,
    getPermissions,
    getRoleEffectivePermissions,
    getRoleInheritance,
    getRolePermissions,
    getRoles,
    replaceRoleInheritance,
    replaceRolePermissions,
    updateRole,
} from '../rbacService'
import type {CreateRoleRequest, PermissionResponse, RoleResponse, UpdateRoleRequest} from '../rbacTypes'
import {RoleEditorDrawer} from './RoleEditorDrawer'
import {RoleInheritanceDrawer} from './RoleInheritanceDrawer'
import {RolePermissionDrawer} from './RolePermissionDrawer'

type RoleFilters = {
    keyword?: string
    status?: string
    builtIn?: string
}

/** Per-row state for the expanded "有效权限" summary, kept separately for every role id. */
type RoleEffectiveState = {
    error?: string
    ids?: number[]
    loading: boolean
}

function RoleListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [saving, setSaving] = useState(false)
    const [permissions, setPermissions] = useState<PermissionResponse[]>([])
    const [editingRole, setEditingRole] = useState<RoleResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [permissionRole, setPermissionRole] = useState<RoleResponse | null>(null)
    const [selectedPermissionIds, setSelectedPermissionIds] = useState<number[]>([])
    const [inheritanceRole, setInheritanceRole] = useState<RoleResponse | null>(null)
    const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([])
    const [effectiveStates, setEffectiveStates] = useState<Record<number, RoleEffectiveState>>({})
    const [filters, setFilters] = useState<RoleFilters>({})
    const loadRolesPage = useCallback(
        (request: { page: number; size: number }) => getRoles(request),
        [],
    )
    const {loading, records: roles, page, size, total, load: loadRoles} = usePageData<RoleResponse>(loadRolesPage)

    async function loadPermissions() {
        const result = await getPermissions({page: 1, size: 500})
        setPermissions(result.records)
    }

    useEffect(() => {
        void loadPermissions()
    }, [])

    const canCreate = canUseRbacButton(auth, rbacButtonCodes.role.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.role.edit)
    const canAssignPermissions = canUseRbacButton(auth, rbacButtonCodes.role.permissions)
    const canEditInheritance = canUseRbacButton(auth, rbacButtonCodes.role.inheritance)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.role.delete)

    /** Filtering runs client-side over the loaded page — the list API has no query params. */
    const visibleRoles = useMemo(() => filterRoles(roles, filters), [filters, roles])

    const columns: ColumnsType<RoleResponse> = [
        {
            title: '角色',
            dataIndex: 'roleCode',
            fixed: 'left',
            width: 240,
            render: (_, record) => <StackedCell primary={record.roleName} secondary={record.roleCode}/>,
        },
        {
            title: '状态',
            dataIndex: 'status',
            width: 150,
            render: (_, record) => (
                <Space size={4} wrap>
                    <StatusTag value={record.status}/>
                    {record.builtIn && <Tag color="blue">内置</Tag>}
                </Space>
            ),
        },
        {title: '排序', dataIndex: 'sortNo', width: 90},
        {title: '描述', dataIndex: 'description', render: (_, record) => record.description || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 190,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: RoleResponse) {
        const actions: RowAction[] = [
            {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined/>,
                hidden: !canEdit,
                onClick: () => openEditor(record),
            },
            {
                key: 'permissions',
                label: '权限',
                icon: <SafetyOutlined/>,
                hidden: !canAssignPermissions,
                onClick: () => void openPermissions(record),
            },
            {
                key: 'inheritance',
                label: '继承',
                icon: <BranchesOutlined/>,
                hidden: !canEditInheritance,
                onClick: () => void openInheritance(record),
            },
            {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined/>,
                danger: true,
                hidden: !canDelete,
                confirm: '确认删除该角色？删除后无法恢复。',
                onClick: () => void removeRole(record.id),
            },
        ]
        return <RowActions actions={actions} maxInline={2}/>
    }

    function openEditor(role?: RoleResponse) {
        setEditingRole(role ?? null)
        setEditorOpen(true)
    }

    async function handleSubmit(request: CreateRoleRequest | UpdateRoleRequest) {
        setSaving(true)
        try {
            if (editingRole) {
                await updateRole(editingRole.id, request)
            } else {
                await createRole(request as CreateRoleRequest)
            }
            message.success('保存成功')
            setEditorOpen(false)
            await loadRoles()
        } finally {
            setSaving(false)
        }
    }

    async function openPermissions(role: RoleResponse) {
        setPermissionRole(role)
        setSelectedPermissionIds(await getRolePermissions(role.id))
    }

    /**
     * Loads one role's effective permissions on demand. Re-runs on every expand so a row
     * opened after a permission or inheritance edit shows the updated set.
     */
    async function loadEffectivePermissions(roleId: number) {
        setEffectiveStates((current) => ({...current, [roleId]: {...current[roleId], loading: true}}))
        try {
            const ids = await getRoleEffectivePermissions(roleId)
            setEffectiveStates((current) => ({...current, [roleId]: {ids, loading: false}}))
        } catch (error) {
            setEffectiveStates((current) => ({
                ...current,
                [roleId]: {error: resolveErrorMessage(error), loading: false},
            }))
        }
    }

    async function savePermissions(ids: number[], syncButtonApis: boolean) {
        if (!permissionRole) return
        setSaving(true)
        try {
            await replaceRolePermissions(permissionRole.id, ids, syncButtonApis)
            message.success('权限已更新')
            setPermissionRole(null)
        } finally {
            setSaving(false)
        }
    }

    async function openInheritance(role: RoleResponse) {
        setInheritanceRole(role)
        setSelectedRoleIds(await getRoleInheritance(role.id))
    }

    async function saveInheritance(ids: number[]) {
        if (!inheritanceRole) return
        setSaving(true)
        try {
            await replaceRoleInheritance(inheritanceRole.id, ids)
            message.success('继承关系已更新')
            setInheritanceRole(null)
        } finally {
            setSaving(false)
        }
    }

    async function removeRole(id: number) {
        await deleteRole(id)
        message.success('角色已删除')
        await loadRoles()
    }

    return (
        <>
            <PageToolbar
                actions={canCreate && (
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建角色</Button>
                )}
                description="维护角色、直接权限和 RBAC1 角色继承。"
                icon={<TeamOutlined/>}
                title="角色管理"
            />
            <FilterBar<RoleFilters>
                onReset={() => setFilters({})}
                onSearch={setFilters}
            >
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="角色编码、名称、描述"/>
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select
                        allowClear
                        options={[
                            {label: '启用', value: 'ENABLED'},
                            {label: '禁用', value: 'DISABLED'},
                        ]}
                        placeholder="全部"
                    />
                </Form.Item>
                <Form.Item label="来源" name="builtIn">
                    <Select
                        allowClear
                        options={[
                            {label: '内置角色', value: 'builtIn'},
                            {label: '自定义角色', value: 'custom'},
                        ]}
                        placeholder="全部"
                    />
                </Form.Item>
            </FilterBar>
            <DataTable<RoleResponse>
                columns={columns}
                count={total}
                dataSource={visibleRoles}
                emptyDescription="调整筛选条件，或新建一个角色并为它分配权限。"
                emptyTitle="没有匹配的角色"
                expandable={{
                    expandedRowRender: (record) => (
                        <RoleEffectiveSummary
                            onRetry={() => void loadEffectivePermissions(record.id)}
                            permissions={permissions}
                            state={effectiveStates[record.id]}
                        />
                    ),
                    onExpand: (expanded, record) => {
                        if (expanded) {
                            void loadEffectivePermissions(record.id)
                        }
                    },
                }}
                loading={loading}
                pagination={{
                    current: page,
                    pageSize: size,
                    total,
                    onChange: voidify(loadRoles),
                }}
                rowKey="id"
                scroll={{x: 960}}
                title="角色列表"
            />
            <RoleEditorDrawer
                loading={saving}
                onClose={() => setEditorOpen(false)}
                onSubmit={handleSubmit}
                open={editorOpen}
                value={editingRole}
            />
            <RolePermissionDrawer
                onClose={() => setPermissionRole(null)}
                onSubmit={savePermissions}
                open={Boolean(permissionRole)}
                permissions={permissions}
                role={permissionRole}
                saving={saving}
                selectedPermissionIds={selectedPermissionIds}
            />
            <RoleInheritanceDrawer
                onClose={() => setInheritanceRole(null)}
                onSubmit={saveInheritance}
                open={Boolean(inheritanceRole)}
                role={inheritanceRole}
                roles={roles}
                saving={saving}
                selectedRoleIds={selectedRoleIds}
            />
        </>
    )
}

function filterRoles(roles: RoleResponse[], filters: RoleFilters) {
    const keyword = filters.keyword?.trim().toLowerCase()
    return roles.filter((role) => {
        if (filters.status && !enumEquals(role.status, filters.status)) {
            return false
        }
        if (filters.builtIn && role.builtIn !== (filters.builtIn === 'builtIn')) {
            return false
        }
        if (!keyword) {
            return true
        }
        return [role.roleCode, role.roleName, role.description]
            .some((field) => field?.toLowerCase().includes(keyword))
    })
}

function RoleEffectiveSummary({onRetry, permissions, state}: {
    onRetry: () => void
    permissions: PermissionResponse[]
    state?: RoleEffectiveState
}) {
    const {error, ids, loading}: RoleEffectiveState = state ?? {loading: true}
    if (error) {
        return <ErrorState inline message={error} onRetry={onRetry} title="有效权限加载失败"/>
    }
    if (loading || !ids) {
        return <Skeleton active paragraph={{rows: 1}} title={false}/>
    }
    const matched = permissions.filter((permission) => ids.includes(permission.id))
    if (!matched.length) {
        return (
            <EmptyState
                description="该角色没有直接分配或继承得到的权限，可通过“权限”“继承”操作分配。"
                inline
                title="暂无有效权限"
            />
        )
    }
    return (
        <Space wrap>
            {matched.map((permission) => (
                <Tag key={permission.id}>{permission.permName}</Tag>
            ))}
        </Space>
    )
}

export const Component = RoleListPage
