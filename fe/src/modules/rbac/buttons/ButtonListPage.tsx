import {ApiOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons'
import {App, Button, Empty, Popconfirm, Select, Space, Table} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
import {StatusTag} from '../../../components/StatusTag'
import {enumEquals} from '../../../utils/enum'
import {flattenTree} from '../../../utils/tree'
import {
    createButton,
    deleteButton,
    getButtonApis,
    getButtons,
    getMenuTree,
    getPermissions,
    replaceButtonApis,
    updateButton,
} from '../rbacService'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from '../permissions/PermissionEditorDrawer'
import {ButtonApiDrawer} from './ButtonApiDrawer'

function ButtonListPage() {
    const {message} = App.useApp()
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [permissions, setPermissions] = useState<PermissionResponse[]>([])
    const [buttons, setButtons] = useState<PermissionResponse[]>([])
    const [selectedMenuId, setSelectedMenuId] = useState<number>()
    const [editingButton, setEditingButton] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [apiButton, setApiButton] = useState<PermissionResponse | null>(null)
    const [selectedApiIds, setSelectedApiIds] = useState<number[]>([])

    async function loadBase() {
        const [menuTree, pageResult] = await Promise.all([
            getMenuTree(),
            getPermissions({page: 1, size: 500}),
        ])
        setMenus(menuTree)
        setPermissions(pageResult.records)
        const firstMenu = flattenTree(menuTree)[0]
        if (firstMenu && !selectedMenuId) {
            setSelectedMenuId(firstMenu.permissionId)
        }
    }

    async function loadButtons(menuId = selectedMenuId) {
        if (!menuId) {
            setButtons([])
            return
        }
        setLoading(true)
        try {
            setButtons(await getButtons(menuId))
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        void loadBase()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    useEffect(() => {
        void loadButtons(selectedMenuId)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedMenuId])

    const apiPermissions = useMemo(
        () => permissions.filter((permission) => enumEquals(permission.permType, 3) || enumEquals(permission.permType, 'API')),
        [permissions],
    )
    const menuOptions = useMemo(
        () => flattenTree(menus).map((menu) => ({
            value: menu.permissionId,
            label: `${menu.permName} (${menu.routePath || menu.permCode})`
        })),
        [menus],
    )

    const columns: ColumnsType<PermissionResponse> = [
        {title: '按钮编码', dataIndex: 'permCode', width: 220},
        {title: '按钮名称', dataIndex: 'permName', width: 160},
        {title: '按钮 Key', width: 120, render: (_, record) => record.button?.buttonKey || '-'},
        {title: '前端动作', width: 180, render: (_, record) => record.button?.frontendAction || '-'},
        {title: '状态', dataIndex: 'status', width: 100, render: (value) => <StatusTag value={value}/>},
        {
            title: '操作',
            fixed: 'right',
            width: 240,
            render: (_, record) => (
                <Space>
                    <Button icon={<EditOutlined/>} size="small" onClick={() => openEditor(record)}>编辑</Button>
                    <Button icon={<ApiOutlined/>} size="small" onClick={() => openApis(record)}>绑定 API</Button>
                    <Popconfirm title="确认删除该按钮？" onConfirm={() => remove(record.id)}>
                        <Button danger size="small">删除</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ]

    function openEditor(button?: PermissionResponse) {
        setEditingButton(button ?? null)
        setEditorOpen(true)
    }

    async function handleSubmit(request: PermissionRequest) {
        setSaving(true)
        try {
            const body = {
                ...request,
                permType: 'BUTTON' as const,
                button: {
                    ...request.button,
                    menuPermissionId: request.button?.menuPermissionId ?? selectedMenuId,
                },
            }
            if (editingButton) {
                await updateButton(editingButton.id, body)
            } else {
                await createButton(body)
            }
            message.success('保存成功')
            setEditorOpen(false)
            await loadButtons()
        } finally {
            setSaving(false)
        }
    }

    async function openApis(button: PermissionResponse) {
        setApiButton(button)
        setSelectedApiIds(await getButtonApis(button.id))
    }

    async function saveApis(ids: number[]) {
        if (!apiButton) return
        setSaving(true)
        try {
            await replaceButtonApis(apiButton.id, ids)
            message.success('API 绑定已更新')
            setApiButton(null)
            await loadButtons()
        } finally {
            setSaving(false)
        }
    }

    async function remove(id: number) {
        await deleteButton(id)
        message.success('按钮已删除')
        await loadButtons()
    }

    return (
        <>
            <PageToolbar
                actions={(
                    <Space>
                        <Select
                            options={menuOptions}
                            placeholder="选择菜单"
                            style={{width: 320}}
                            value={selectedMenuId}
                            onChange={setSelectedMenuId}
                        />
                        <Button disabled={!selectedMenuId} icon={<PlusOutlined/>} onClick={() => openEditor()}
                                type="primary">
                            新建按钮
                        </Button>
                    </Space>
                )}
                description="按菜单维护按钮权限，并绑定按钮会调用的 API 权限。"
                title="按钮管理"
            />
            {selectedMenuId ? (
                <Table<PermissionResponse>
                    columns={columns}
                    dataSource={buttons}
                    loading={loading}
                    pagination={false}
                    rowKey="id"
                    scroll={{x: 980}}
                />
            ) : (
                <Empty description="请先创建或选择菜单"/>
            )}
            <PermissionEditorDrawer
                apiPermissions={apiPermissions}
                fixedType="BUTTON"
                loading={saving}
                menus={menus}
                onClose={() => setEditorOpen(false)}
                onSubmit={handleSubmit}
                open={editorOpen}
                title={editingButton ? '编辑按钮' : '新建按钮'}
                value={editingButton}
            />
            <ButtonApiDrawer
                apiPermissions={apiPermissions}
                button={apiButton}
                onClose={() => setApiButton(null)}
                onSubmit={saveApis}
                open={Boolean(apiButton)}
                saving={saving}
                selectedIds={selectedApiIds}
            />
        </>
    )
}

export const Component = ButtonListPage
