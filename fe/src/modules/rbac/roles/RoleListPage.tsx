import {BranchesOutlined, EditOutlined, PlusOutlined, SafetyOutlined} from '@ant-design/icons'
import {App, Button, Popconfirm, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useCallback, useEffect, useState} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
import {StatusTag} from '../../../components/StatusTag'
import {usePageData} from '../../../hooks/usePageData'
import {voidify} from '../../../utils/async'
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
    const [effectiveIds, setEffectiveIds] = useState<number[]>([])
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

    const columns: ColumnsType<RoleResponse> = [
        {title: '角色编码', dataIndex: 'roleCode', width: 180},
        {title: '角色名称', dataIndex: 'roleName', width: 160},
        {title: '状态', dataIndex: 'status', width: 100, render: (_, record) => <StatusTag value={record.status}/>},
        {title: '排序', dataIndex: 'sortNo', width: 90},
        {
            title: '内置',
            dataIndex: 'builtIn',
            width: 90,
            render: (_, record) => record.builtIn ? <Tag color="blue">内置</Tag> : '-'
        },
        {title: '描述', dataIndex: 'description', render: (_, record) => record.description || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 310,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: RoleResponse) {
        const actions: ReactNode[] = []
        if (canEdit) {
            actions.push(<Button icon={<EditOutlined/>} key="edit" size="small"
                                 onClick={() => openEditor(record)}>编辑</Button>)
        }
        if (canAssignPermissions) {
            actions.push(<Button icon={<SafetyOutlined/>} key="permissions" size="small"
                                 onClick={() => void openPermissions(record)}>权限</Button>)
        }
        if (canEditInheritance) {
            actions.push(
                <Button icon={<BranchesOutlined/>} key="inheritance" size="small"
                        onClick={() => void openInheritance(record)}>
                    继承
                </Button>,
            )
        }
        if (canDelete) {
            actions.push(
                <Popconfirm key="delete" title="确认删除该角色？" onConfirm={() => void removeRole(record.id)}>
                    <Button danger size="small">删除</Button>
                </Popconfirm>,
            )
        }
        return actions.length ? <Space>{actions}</Space> : '-'
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
        const [directIds, effectivePermissionIds] = await Promise.all([
            getRolePermissions(role.id),
            getRoleEffectivePermissions(role.id),
        ])
        setSelectedPermissionIds(directIds)
        setEffectiveIds(effectivePermissionIds)
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
                actions={canCreate &&
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建角色</Button>}
                description="维护角色、直接权限和 RBAC1 角色继承。"
                title="角色管理"
            />
            <Table<RoleResponse>
                columns={columns}
                dataSource={roles}
                expandable={{
                    expandedRowRender: () => <RoleEffectiveSummary permissions={permissions} ids={effectiveIds}/>,
                }}
                loading={loading}
                pagination={{
                    current: page,
                    pageSize: size,
                    total,
                    showSizeChanger: true,
                    onChange: voidify(loadRoles),
                }}
                rowKey="id"
                scroll={{x: 1160}}
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

function RoleEffectiveSummary({permissions, ids}: { permissions: PermissionResponse[]; ids: number[] }) {
    const matched = permissions.filter((permission) => ids.includes(permission.id))
    if (!matched.length) {
        return <span>展开前请先点击“权限”加载有效权限，或该角色暂无有效权限。</span>
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
