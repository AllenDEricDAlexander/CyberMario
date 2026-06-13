import {EditOutlined, PlusOutlined} from '@ant-design/icons'
import {App, Button, Popconfirm, Space, Table} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {ReactNode} from 'react'
import {useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
import {PermissionTypeTag} from '../../../components/PermissionTypeTag'
import {StatusTag} from '../../../components/StatusTag'
import {enumEquals} from '../../../utils/enum'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {
    createPermission,
    deletePermission,
    getMenuTree,
    getPermissions,
    updatePermission,
    updatePermissionStatus,
} from '../rbacService'
import {rbacButtonCodes} from '../rbacPermissionCodes'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from './PermissionEditorDrawer'

function PermissionListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [permissions, setPermissions] = useState<PermissionResponse[]>([])
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [page, setPage] = useState(1)
    const [size, setSize] = useState(50)
    const [total, setTotal] = useState(0)
    const [editingPermission, setEditingPermission] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)

    async function load(nextPage = page, nextSize = size) {
        setLoading(true)
        try {
            const [pageResult, menuTree] = await Promise.all([
                getPermissions({page: nextPage, size: nextSize}),
                getMenuTree(),
            ])
            setPermissions(pageResult.records)
            setMenus(menuTree)
            setPage(pageResult.page)
            setSize(pageResult.size)
            setTotal(pageResult.total)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        void load(1, size)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    const apiPermissions = useMemo(
        () => permissions.filter((permission) => enumEquals(permission.permType, 3) || enumEquals(permission.permType, 'API')),
        [permissions],
    )
    const canCreate = canUseRbacButton(auth, rbacButtonCodes.permission.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.permission.edit)
    const canChangeStatus = canUseRbacButton(auth, rbacButtonCodes.permission.status)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.permission.delete)

    const columns: ColumnsType<PermissionResponse> = [
        {title: '权限编码', dataIndex: 'permCode', width: 220},
        {title: '权限名称', dataIndex: 'permName', width: 180},
        {title: '类型', dataIndex: 'permType', width: 100, render: (value) => <PermissionTypeTag value={value}/>},
        {title: '状态', dataIndex: 'status', width: 100, render: (value) => <StatusTag value={value}/>},
        {title: '父权限', dataIndex: 'parentId', width: 100, render: (value) => value || '-'},
        {title: '排序', dataIndex: 'sortNo', width: 80},
        {title: '描述', dataIndex: 'description', render: (value) => value || '-'},
        {
            title: '操作',
            fixed: 'right',
            width: 220,
            render: (_, record) => renderActions(record),
        },
    ]

    function renderActions(record: PermissionResponse) {
        const actions: ReactNode[] = []
        if (canEdit) {
            actions.push(<Button icon={<EditOutlined/>} key="edit" size="small"
                                 onClick={() => openEditor(record)}>编辑</Button>)
        }
        if (canChangeStatus) {
            actions.push(
                <Popconfirm key="status" title="确认切换权限状态？" onConfirm={() => toggleStatus(record)}>
                    <Button size="small">{isEnabled(record) ? '禁用' : '启用'}</Button>
                </Popconfirm>,
            )
        }
        if (canDelete) {
            actions.push(
                <Popconfirm key="delete" title="确认删除该权限？" onConfirm={() => remove(record.id)}>
                    <Button danger size="small">删除</Button>
                </Popconfirm>,
            )
        }
        return actions.length ? <Space>{actions}</Space> : '-'
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
                actions={canCreate &&
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建权限</Button>}
                description="统一维护菜单、按钮和 API 权限。"
                title="权限管理"
            />
            <Table<PermissionResponse>
                columns={columns}
                dataSource={permissions}
                loading={loading}
                pagination={{current: page, pageSize: size, total, showSizeChanger: true, onChange: load}}
                rowKey="id"
                scroll={{x: 1180}}
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

function isEnabled(permission: PermissionResponse) {
    return enumEquals(permission.status, 1) || enumEquals(permission.status, 'ENABLED')
}

export const Component = PermissionListPage
