import {DeleteOutlined, EditOutlined, MenuOutlined, PlusOutlined} from '@ant-design/icons'
import {App, Button, Flex, Form, Input, Tag, Tree, Typography} from 'antd'
import type {DataNode} from 'antd/es/tree'
import type {Key} from 'react'
import {useEffect, useMemo, useState} from 'react'
import {EmptyState} from '../../../components/EmptyState'
import {FilterBar} from '../../../components/FilterBar'
import {PageToolbar} from '../../../components/PageToolbar'
import {RowActions, type RowAction} from '../../../components/RowActions'
import {enumEquals} from '../../../utils/enum'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
import {rbacButtonCodes} from '../rbacPermissionCodes'
import {createMenu, deleteMenu, getMenuTree, getPermissions, updateMenu} from '../rbacService'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from '../permissions/PermissionEditorDrawer'

type MenuTreeNode = DataNode & {
    raw: MenuTreeResponse
}

type MenuFilters = {
    keyword?: string
}

function MenuTreePage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [permissions, setPermissions] = useState<PermissionResponse[]>([])
    const [editingMenu, setEditingMenu] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [filters, setFilters] = useState<MenuFilters>({})
    const [expandedKeys, setExpandedKeys] = useState<Key[]>([])

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

    /** Filtering runs client-side over the loaded tree — the menu API has no query params. */
    const visibleMenus = useMemo(() => filterMenuTree(menus, filters.keyword), [filters.keyword, menus])
    const treeData = useMemo<MenuTreeNode[]>(() => toTreeData(visibleMenus), [visibleMenus])

    // Mirrors the previous `defaultExpandAll`, but also re-expands after a search
    // narrows the tree. Collapsing afterwards still sticks until the next search.
    useEffect(() => {
        setExpandedKeys(collectKeys(treeData))
    }, [treeData])

    const menuPermissions = useMemo(
        () => permissions.filter((permission) => enumEquals(permission.permType, 1) || enumEquals(permission.permType, 'MENU')),
        [permissions],
    )
    const canCreate = canUseRbacButton(auth, rbacButtonCodes.menu.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.menu.edit)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.menu.delete)

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

    function renderNodeTitle(node: MenuTreeNode) {
        const actions: RowAction[] = [
            {
                key: 'edit',
                label: '编辑',
                icon: <EditOutlined/>,
                hidden: !canEdit,
                onClick: () => openEditor(node.raw),
            },
            {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined/>,
                danger: true,
                hidden: !canDelete,
                confirm: '确认删除该菜单？子菜单需要先移除。',
                onClick: () => void remove(Number(node.key)),
            },
        ]
        return (
            <Flex align="center" gap="small" justify="space-between">
                <Flex align="center" gap="small">
                    <span>{node.raw.permName}</span>
                    <Typography.Text type="secondary">
                        {node.raw.routePath || node.raw.permCode}
                    </Typography.Text>
                    {node.raw.hidden && <Tag>隐藏</Tag>}
                </Flex>
                <RowActions actions={actions} emptyText={null} maxInline={2}/>
            </Flex>
        )
    }

    return (
        <>
            <PageToolbar
                actions={canCreate && (
                    <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建菜单</Button>
                )}
                description="以树形结构维护前端菜单权限。"
                icon={<MenuOutlined/>}
                title="菜单管理"
            />
            <FilterBar<MenuFilters>
                loading={loading}
                onReset={() => setFilters({})}
                onSearch={setFilters}
            >
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="菜单名称、编码、路由"/>
                </Form.Item>
            </FilterBar>
            <div className="split-panel">
                {treeData.length ? (
                    <Tree<MenuTreeNode>
                        blockNode
                        disabled={loading}
                        expandedKeys={expandedKeys}
                        onExpand={setExpandedKeys}
                        titleRender={renderNodeTitle}
                        treeData={treeData}
                    />
                ) : (
                    <EmptyState
                        action={canCreate && (
                            <Button icon={<PlusOutlined/>} onClick={() => openEditor()} type="primary">新建菜单</Button>
                        )}
                        description={filters.keyword
                            ? '换一个关键词试试，或清空筛选查看完整菜单树。'
                            : '还没有配置任何菜单，先创建一个顶层菜单再逐级补充子菜单。'}
                        title={filters.keyword ? '没有匹配的菜单' : '暂无菜单'}
                    />
                )}
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

/** Keeps a branch when the node itself matches, or when any descendant does. */
function filterMenuTree(menus: MenuTreeResponse[], rawKeyword?: string): MenuTreeResponse[] {
    const keyword = rawKeyword?.trim().toLowerCase()
    if (!keyword) {
        return menus
    }
    return menus.flatMap((menu) => {
        if (matchesMenu(menu, keyword)) {
            return [menu]
        }
        const children = filterMenuTree(menu.children ?? [], keyword)
        return children.length ? [{...menu, children}] : []
    })
}

function matchesMenu(menu: MenuTreeResponse, keyword: string) {
    return [menu.permName, menu.permCode, menu.routePath, menu.routeName]
        .some((field) => field?.toLowerCase().includes(keyword))
}

function toTreeData(menus: MenuTreeResponse[]): MenuTreeNode[] {
    return menus.map((menu) => ({
        key: menu.permissionId,
        title: `${menu.permName} (${menu.routePath || menu.permCode})`,
        raw: menu,
        children: toTreeData(menu.children ?? []),
    }))
}

function collectKeys(nodes: MenuTreeNode[]): Key[] {
    return nodes.flatMap((node) => [node.key, ...collectKeys((node.children ?? []) as MenuTreeNode[])])
}

export const Component = MenuTreePage
