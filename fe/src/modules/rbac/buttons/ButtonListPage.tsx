import {ApiOutlined, AppstoreOutlined, DeleteOutlined, EditOutlined, PlusOutlined} from '@ant-design/icons'
import {App, Button, Form, Input, Select} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useCallback, useEffect, useMemo, useState} from 'react'
import {DataTable} from '../../../components/DataTable'
import {EmptyState} from '../../../components/EmptyState'
import {FilterBar} from '../../../components/FilterBar'
import {PageToolbar} from '../../../components/PageToolbar'
import {RowActions, type RowAction} from '../../../components/RowActions'
import {StackedCell} from '../../../components/StackedCell'
import {StatusTag} from '../../../components/StatusTag'
import {enumEquals} from '../../../utils/enum'
import {flattenTree} from '../../../utils/tree'
import {canUseRbacButton, useAuth} from '../../auth/authStore'
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
import {rbacButtonCodes} from '../rbacPermissionCodes'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'
import {PermissionEditorDrawer} from '../permissions/PermissionEditorDrawer'
import {ButtonApiDrawer} from './ButtonApiDrawer'

type ButtonFilters = {
    keyword?: string
}

function ButtonListPage() {
    const {message} = App.useApp()
    const auth = useAuth()
    const [loading, setLoading] = useState(false)
    const [saving, setSaving] = useState(false)
    const [menus, setMenus] = useState<MenuTreeResponse[]>([])
    const [permissions, setPermissions] = useState<PermissionResponse[]>([])
    const [buttons, setButtons] = useState<PermissionResponse[]>([])
    const [selectedMenuId, setSelectedMenuId] = useState<number>()
    const [keyword, setKeyword] = useState<string>()
    const [editingButton, setEditingButton] = useState<PermissionResponse | null>(null)
    const [editorOpen, setEditorOpen] = useState(false)
    const [apiButton, setApiButton] = useState<PermissionResponse | null>(null)
    const [selectedApiIds, setSelectedApiIds] = useState<number[]>([])

    const loadBase = useCallback(async () => {
        const [menuTree, pageResult] = await Promise.all([
            getMenuTree(),
            getPermissions({page: 1, size: 500}),
        ])
        setMenus(menuTree)
        setPermissions(pageResult.records)
        const firstMenu = flattenTree(menuTree)[0]
        if (firstMenu) {
            setSelectedMenuId((current) => current ?? firstMenu.permissionId)
        }
    }, [])

    const loadButtons = useCallback(async (menuId = selectedMenuId) => {
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
    }, [selectedMenuId])

    useEffect(() => {
        void loadBase()
    }, [loadBase])

    useEffect(() => {
        void loadButtons(selectedMenuId)
    }, [loadButtons, selectedMenuId])

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
    /** Filtering runs client-side — the button API only takes a menu id. */
    const visibleButtons = useMemo(() => filterButtons(buttons, keyword), [buttons, keyword])
    const canCreate = canUseRbacButton(auth, rbacButtonCodes.button.create)
    const canEdit = canUseRbacButton(auth, rbacButtonCodes.button.edit)
    const canBindApis = canUseRbacButton(auth, rbacButtonCodes.button.apis)
    const canDelete = canUseRbacButton(auth, rbacButtonCodes.button.delete)

    const columns: ColumnsType<PermissionResponse> = [
        {
            title: '按钮',
            dataIndex: 'permCode',
            fixed: 'left',
            width: 260,
            render: (_, record) => <StackedCell primary={record.permName} secondary={record.permCode}/>,
        },
        {
            title: '按钮 Key / 前端动作',
            width: 220,
            render: (_, record) => (
                <StackedCell
                    plain
                    primary={record.button?.buttonKey || '-'}
                    secondary={record.button?.frontendAction || '未配置前端动作'}
                />
            ),
        },
        {title: '状态', dataIndex: 'status', width: 100, render: (_, record) => <StatusTag value={record.status}/>},
        {
            title: '操作',
            fixed: 'right',
            width: 190,
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
                key: 'apis',
                label: '绑定 API',
                icon: <ApiOutlined/>,
                hidden: !canBindApis,
                onClick: () => void openApis(record),
            },
            {
                key: 'delete',
                label: '删除',
                icon: <DeleteOutlined/>,
                danger: true,
                hidden: !canDelete,
                confirm: '确认删除该按钮？删除后无法恢复。',
                onClick: () => void remove(record.id),
            },
        ]
        return <RowActions actions={actions} maxInline={2}/>
    }

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
                actions={canCreate && (
                    <Button disabled={!selectedMenuId} icon={<PlusOutlined/>} onClick={() => openEditor()}
                            type="primary">
                        新建按钮
                    </Button>
                )}
                description="按菜单维护按钮权限，并绑定按钮会调用的 API 权限。"
                icon={<AppstoreOutlined/>}
                title="按钮管理"
            />
            <FilterBar<ButtonFilters>
                loading={loading}
                onReset={() => setKeyword(undefined)}
                onSearch={(values) => setKeyword(values.keyword)}
            >
                {/* Switching menu reloads the list straight away, so it stays outside the form values. */}
                <Form.Item label="所属菜单">
                    <Select
                        onChange={setSelectedMenuId}
                        optionFilterProp="label"
                        options={menuOptions}
                        placeholder="选择菜单"
                        showSearch
                        value={selectedMenuId}
                    />
                </Form.Item>
                <Form.Item label="关键词" name="keyword">
                    <Input allowClear placeholder="按钮编码、名称、Key、前端动作"/>
                </Form.Item>
            </FilterBar>
            {selectedMenuId ? (
                <DataTable<PermissionResponse>
                    columns={columns}
                    count={visibleButtons.length}
                    dataSource={visibleButtons}
                    emptyDescription="该菜单下还没有按钮权限，新建后即可绑定对应的 API。"
                    emptyTitle="没有匹配的按钮"
                    loading={loading}
                    pagination={false}
                    rowKey="id"
                    scroll={{x: 800}}
                    title="按钮列表"
                />
            ) : (
                <div className="state-card">
                    <EmptyState
                        description="按钮权限归属于菜单，请先在“菜单管理”中创建菜单，再回到这里选择它。"
                        title="请先选择菜单"
                    />
                </div>
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

function filterButtons(buttons: PermissionResponse[], rawKeyword?: string) {
    const keyword = rawKeyword?.trim().toLowerCase()
    if (!keyword) {
        return buttons
    }
    return buttons.filter((button) => [
        button.permCode,
        button.permName,
        button.button?.buttonKey,
        button.button?.frontendAction,
    ].some((field) => field?.toLowerCase().includes(keyword)))
}

export const Component = ButtonListPage
