import {
    BulbFilled,
    BulbOutlined,
    ColumnHeightOutlined,
    ColumnWidthOutlined,
    LogoutOutlined,
    MenuFoldOutlined,
    MenuOutlined,
    MenuUnfoldOutlined,
    SearchOutlined,
    SettingOutlined,
    UserOutlined,
} from '@ant-design/icons'
import {Avatar, Breadcrumb, Button, Drawer, Dropdown, Grid, Input, Layout, Menu, Result, Tooltip, Typography} from 'antd'
import {useEffect, useMemo, useState} from 'react'
import {Link, Outlet, useLocation, useNavigate} from 'react-router'
import {VisualBackdrop} from '../../components/VisualBackdrop'
import {hasAdminPermissionBypass, useAuth} from '../../modules/auth/authStore'
import {useThemeMode} from '../../theme/themeMode'
import {voidify} from '../../utils/async'
import {
    adminMenuBreadcrumb,
    buildAuthorizedAdminMenuItems,
    canAccessAdminPath,
    filterMenuItemsByKeyword,
    findMenuPath,
    flattenMenuKeys,
    openMenuKeysFor,
    selectedAdminMenuKey,
} from './menu'
import {isCurrentPathAffectedByLostButtons} from './permissionImpact'

const {Header, Sider, Content} = Layout

const COLLAPSED_STORAGE_KEY = 'cm.layout.siderCollapsed'

/** Role lists get long; the header only has room for one plus a count. */
function summarizeRoles(roleCodes: string[]) {
    return roleCodes.length > 1 ? `${roleCodes[0]} +${roleCodes.length - 1}` : roleCodes[0]
}

function readCollapsed() {
    if (typeof window === 'undefined') {
        return false
    }
    try {
        return window.localStorage.getItem(COLLAPSED_STORAGE_KEY) === '1'
    } catch {
        return false
    }
}

export function AdminLayout() {
    const auth = useAuth()
    const location = useLocation()
    const navigate = useNavigate()
    const screens = Grid.useBreakpoint()
    const {mode, density, toggleMode, toggleDensity} = useThemeMode()
    const [collapsed, setCollapsed] = useState(readCollapsed)
    const [mobileNavOpen, setMobileNavOpen] = useState(false)
    const [menuKeyword, setMenuKeyword] = useState('')
    const [contentVersion, setContentVersion] = useState(0)

    // `screens.lg` is undefined during the first render, so treat only an
    // explicit `false` as mobile to avoid a flash of the drawer layout.
    const isMobile = screens.lg === false
    const canBypassMenuPermissions = hasAdminPermissionBypass(auth)

    const menuItems = useMemo(
        () => buildAuthorizedAdminMenuItems(auth.menus, canBypassMenuPermissions, auth.roleCodes),
        [auth.menus, auth.roleCodes, canBypassMenuPermissions],
    )
    const visibleMenuItems = useMemo(
        () => filterMenuItemsByKeyword(menuItems, menuKeyword),
        [menuItems, menuKeyword],
    )
    const menuKeys = useMemo(() => flattenMenuKeys(menuItems), [menuItems])
    const canAccessCurrentPath = useMemo(
        () => canAccessAdminPath(location.pathname, auth.menus, canBypassMenuPermissions, auth.roleCodes),
        [auth.menus, auth.roleCodes, canBypassMenuPermissions, location.pathname],
    )

    const selectedKeys = useMemo(() => {
        const matched = selectedAdminMenuKey(location.pathname, menuKeys)
        return matched ? [matched] : ['/chat']
    }, [location.pathname, menuKeys])

    const [openKeys, setOpenKeys] = useState<string[]>(() => openMenuKeysFor(location.pathname, menuItems))

    // Keep the active group expanded as the route changes, without fighting the
    // user when they collapse a group manually.
    useEffect(() => {
        const required = openMenuKeysFor(location.pathname, menuItems)
        if (required.length === 0) {
            return
        }
        setOpenKeys((current) => (
            required.every((key) => current.includes(key)) ? current : [...current, ...required]
        ))
    }, [location.pathname, menuItems])

    const breadcrumbItems = useMemo(
        () => adminMenuBreadcrumb(location.pathname, menuItems),
        [location.pathname, menuItems],
    )

    useEffect(() => {
        if (!auth.permissionChange) {
            return
        }
        if (isCurrentPathAffectedByLostButtons(location.pathname, auth.permissionChange.lostButtonCodes)) {
            setContentVersion((value) => value + 1)
        }
    }, [auth.permissionChange, location.pathname])

    // The drawer must not linger after navigating on a phone.
    useEffect(() => {
        setMobileNavOpen(false)
    }, [location.pathname])

    async function handleLogout() {
        await auth.logout()
        void navigate('/login', {replace: true})
    }

    function toggleCollapsed() {
        setCollapsed((value) => {
            const next = !value
            try {
                window.localStorage.setItem(COLLAPSED_STORAGE_KEY, next ? '1' : '0')
            } catch {
                // Preference persistence is best-effort.
            }
            return next
        })
    }

    function handleMenuClick(key: string) {
        const path = findMenuPath(key)
        if (path) {
            void navigate(path)
        }
    }

    const navigation = (
        <Menu
            items={visibleMenuItems}
            mode="inline"
            onClick={({key}) => handleMenuClick(String(key))}
            onOpenChange={(keys) => setOpenKeys(keys.map(String))}
            openKeys={menuKeyword ? undefined : openKeys}
            selectedKeys={selectedKeys}
            theme="dark"
        />
    )

    const siderContent = (showSearch: boolean) => (
        <>
            <Link className="admin-brand" to="/">
                <span className="admin-brand-mark">C</span>
                {(!collapsed || isMobile) && (
                    <span className="admin-brand-text">
                        <span className="admin-brand-name">CyberMario</span>
                        <span className="admin-brand-tagline">Agent Workspace</span>
                    </span>
                )}
            </Link>
            {showSearch && (
                <div className="admin-sider-search">
                    <Input
                        allowClear
                        onChange={(event) => setMenuKeyword(event.target.value)}
                        placeholder="搜索菜单"
                        prefix={<SearchOutlined/>}
                        value={menuKeyword}
                    />
                </div>
            )}
            {visibleMenuItems.length > 0
                ? navigation
                : <div className="admin-sider-empty">没有匹配的菜单</div>}
        </>
    )

    return (
        <Layout className="admin-layout">
            {!isMobile && (
                <Sider
                    className="admin-sider"
                    collapsed={collapsed}
                    collapsedWidth={72}
                    collapsible
                    trigger={null}
                    width={240}
                >
                    {siderContent(!collapsed)}
                    <div className={collapsed ? 'admin-sider-footer is-collapsed' : 'admin-sider-footer'}>
                        <Tooltip placement="right" title={collapsed ? '展开菜单' : '收起菜单'}>
                            <Button
                                aria-label={collapsed ? '展开菜单' : '收起菜单'}
                                icon={collapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>}
                                onClick={toggleCollapsed}
                                type="text"
                            />
                        </Tooltip>
                        {!collapsed && (
                            <Tooltip title={density === 'compact' ? '切换为舒适密度' : '切换为紧凑密度'}>
                                <Button
                                    aria-label="切换界面密度"
                                    icon={density === 'compact' ? <ColumnHeightOutlined/> : <ColumnWidthOutlined/>}
                                    onClick={toggleDensity}
                                    type="text"
                                />
                            </Tooltip>
                        )}
                    </div>
                </Sider>
            )}

            {isMobile && (
                <Drawer
                    className="admin-sider"
                    closable={false}
                    onClose={() => setMobileNavOpen(false)}
                    open={mobileNavOpen}
                    placement="left"
                    styles={{body: {padding: 0, display: 'flex', flexDirection: 'column'}}}
                    size={264}
                >
                    {siderContent(true)}
                </Drawer>
            )}

            <Layout>
                <Header className="admin-header">
                    <div className="admin-header-nav">
                        {isMobile && (
                            <Button
                                aria-label="打开菜单"
                                icon={<MenuOutlined/>}
                                onClick={() => setMobileNavOpen(true)}
                                type="text"
                            />
                        )}
                        {breadcrumbItems.length > 0 && (
                            <nav aria-label="面包屑" className="admin-header-breadcrumb">
                                <Breadcrumb
                                    items={breadcrumbItems.map((item) => ({
                                        title: item.to ? <Link to={item.to}>{item.title}</Link> : item.title,
                                    }))}
                                />
                            </nav>
                        )}
                    </div>

                    <div className="admin-header-actions">
                        <Tooltip title={mode === 'dark' ? '切换到浅色模式' : '切换到深色模式'}>
                            <Button
                                aria-label="切换主题"
                                icon={mode === 'dark' ? <BulbFilled/> : <BulbOutlined/>}
                                onClick={toggleMode}
                                type="text"
                            />
                        </Tooltip>
                        <span className="admin-header-divider"/>
                        <Dropdown
                            menu={{
                                items: [
                                    {
                                        key: 'account-settings',
                                        icon: <SettingOutlined/>,
                                        label: '个人设置',
                                        onClick: () => void navigate('/account/settings'),
                                    },
                                    {type: 'divider'},
                                    {
                                        key: 'logout',
                                        icon: <LogoutOutlined/>,
                                        label: '退出登录',
                                        danger: true,
                                        onClick: voidify(handleLogout),
                                    },
                                ],
                            }}
                            placement="bottomRight"
                        >
                            <Button className="admin-user-button" type="text">
                                <Avatar icon={<UserOutlined/>} size={28} src={auth.user?.avatarUrl}/>
                                <span className="admin-user-meta">
                                    <span className="admin-user-name">
                                        {auth.user?.nickname || auth.user?.username}
                                    </span>
                                    {auth.roleCodes.length > 0 && (
                                        <span className="admin-user-role">{summarizeRoles(auth.roleCodes)}</span>
                                    )}
                                </span>
                            </Button>
                        </Dropdown>
                    </div>
                </Header>

                <Content className="admin-content">
                    <VisualBackdrop variant="content"/>
                    <div className="admin-content-inner">
                        {canAccessCurrentPath ? (
                            <Outlet key={contentVersion}/>
                        ) : (
                            <Result
                                extra={<Button onClick={() => void navigate('/')} type="primary">返回首页</Button>}
                                status="403"
                                subTitle={
                                    <Typography.Text type="secondary">
                                        当前账号没有该菜单权限，如需访问请联系管理员分配角色。
                                    </Typography.Text>
                                }
                                title="无权访问"
                            />
                        )}
                    </div>
                </Content>
            </Layout>
        </Layout>
    )
}
