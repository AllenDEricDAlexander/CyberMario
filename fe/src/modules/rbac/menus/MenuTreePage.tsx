import {EditOutlined, PlusOutlined} from '@ant-design/icons'
import {App, Button, Popconfirm, Space, Tree} from 'antd'
import type {DataNode} from 'antd/es/tree'
import {useEffect, useMemo, useState} from 'react'
import {PageToolbar} from '../../../components/PageToolbar'
import {enumEquals} from '../../../utils/enum'
import {createMenu, deleteMenu, getMenuTree, getPermissions, updateMenu} from '../rbacService'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from '../permissions/PermissionEditorDrawer'

type MenuTreeNode = DataNode & {
    raw: MenuTreeResponse
}

function MenuTreePage() {
    const {message} = App.useApp()
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [permissions, setPermissions] = useState<PermissionResponse[]>([])
    const [editingMenu, setEditingMenu] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)

    async function load() {
        setLoading(true)
        try {
            const [menuTree, pageResult] = await Promise.all([
                getMenuTree(),
                getPermissions({page: 1, size: 500}),
            ])
            setMenus(menuTree)
            setPermissions(pageResult.records)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        void load()
    }, [])

    const treeData = useMemo<MenuTreeNode[]>(() => toTreeData(menus), [menus])
    const menuPermissions = useMemo(
        () => permissions.filter((permission) => enumEquals(permission.permType, 1) || enumEquals(permission.permType, 'MENU')),
        [permissions],
    )

    function openEditor(menu?: MenuTreeResponse) {
        const permission = menu ? menuPermissions.find((item) => item.id === menu.permissionId) ?? null : null
        setEditingMenu(permission)
        setEditorOpen(true)
    }

    async function handleSubmit(request: PermissionRequest) {
        setSaving(true)
        try {
            if (editingMenu) {
                await updateMenu(editingMenu.id, request)
            } else {
                await createMenu({...request, permType: 'MENU'})
            }
            message.success('保存成功')
            setEditorOpen(false)
            await load()
        } finally {
            setSaving(false)
        }
    }

    async function remove(id: number) {
        await deleteMenu(id)
        message.success('菜单已删除')
        await load()
    }

    return (
        <>
            <PageToolbar
                actions={<Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建菜单</Button>}
                description="以树形结构维护前端菜单权限。"
                title="菜单管理"
            />
            <div className="split-panel">
                <Tree<MenuTreeNode>
                    blockNode
                    defaultExpandAll
                    disabled={loading}
                    titleRender={(node) => (
                        <Space>
                            <span>{node.title as string}</span>
                            <Button icon={<EditOutlined/>} size="small"
                                    onClick={() => openEditor(node.raw)}>编辑</Button>
                            <Popconfirm title="确认删除该菜单？" onConfirm={() => remove(Number(node.key))}>
                                <Button danger size="small">删除</Button>
                            </Popconfirm>
                        </Space>
                    )}
                    treeData={treeData}
                />
            </div>
            <PermissionEditorDrawer
                fixedType="MENU"
                loading={saving}
                menus={menus}
                onClose={() => setEditorOpen(false)}
                onSubmit={handleSubmit}
                open={editorOpen}
                title={editingMenu ? '编辑菜单' : '新建菜单'}
                value={editingMenu}
            />
        </>
    )
}

function toTreeData(menus: MenuTreeResponse[]): MenuTreeNode[] {
    return menus.map((menu) => ({
        key: menu.permissionId,
        title: `${menu.permName} (${menu.routePath || menu.permCode})`,
        raw: menu,
        children: toTreeData(menu.children ?? []),
    }))
}

export const Component = MenuTreePage
