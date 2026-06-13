import {LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, ReloadOutlined, UserOutlined} from '@ant-design/icons'
import {Avatar, Button, Dropdown, Layout, Menu, Result, Space, Typography} from 'antd'
import {useMemo, useState} from 'react'
import {Outlet, useLocation, useNavigate} from 'react-router'
import {hasAdminPermissionBypass, useAuth} from '../../modules/auth/authStore'
import {buildAuthorizedAdminMenuItems, canAccessAdminPath, findMenuPath, flattenMenuKeys} from './menu'

const {Header, Sider, Content} = Layout

export function AdminLayout() {
    const auth = useAuth()
    const location = useLocation()
    const navigate = useNavigate()
    const [collapsed, setCollapsed] = useState(false)
    const canBypassMenuPermissions = hasAdminPermissionBypass(auth)
    const menuItems = useMemo(
        () => buildAuthorizedAdminMenuItems(auth.menus, canBypassMenuPermissions),
        [auth.menus, canBypassMenuPermissions],
    )
    const menuKeys = useMemo(() => flattenMenuKeys(menuItems), [menuItems])
    const canAccessCurrentPath = useMemo(
        () => canAccessAdminPath(location.pathname, auth.menus, canBypassMenuPermissions),
        [auth.menus, canBypassMenuPermissions, location.pathname],
    )

    const selectedKeys = useMemo(() => {
        const matched = menuKeys.find((key) => location.pathname === key || location.pathname.startsWith(`${key}/`))
        return matched ? [matched] : ['/chat']
    }, [location.pathname, menuKeys])

    async function handleLogout() {
        await auth.logout()
        navigate('/login', {replace: true})
    }

    return (
        <Layout className="admin-layout">
            <Sider
                breakpoint="lg"
                className="admin-sider"
                collapsed={collapsed}
                collapsible
                trigger={null}
                width={236}
            >
                <div className="admin-logo">
                    <span className="admin-logo-mark">C</span>
                    {!collapsed && <span>CyberMario</span>}
                </div>
                <Menu
                    items={menuItems}
                    mode="inline"
                    onClick={({key}) => {
                        const path = findMenuPath(String(key))
                        if (path) {
                            navigate(path)
                        }
                    }}
                    selectedKeys={selectedKeys}
                    theme="dark"
                />
            </Sider>

            <Layout>
                <Header className="admin-header">
                    <Button
                        aria-label={collapsed ? '展开菜单' : '收起菜单'}
                        icon={collapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>}
                        onClick={() => setCollapsed((value) => !value)}
                        type="text"
                    />
                    <Space className="admin-header-actions">
                        <Button icon={<ReloadOutlined/>} onClick={auth.reload}>
                            刷新权限
                        </Button>
                        <Dropdown
                            menu={{
                                items: [
                                    {
                                        key: 'logout',
                                        icon: <LogoutOutlined/>,
                                        label: '退出登录',
                                        onClick: handleLogout,
                                    },
                                ],
                            }}
                            placement="bottomRight"
                        >
                            <Button type="text">
                                <Space>
                                    <Avatar icon={<UserOutlined/>} size="small"/>
                                    <Typography.Text>{auth.user?.nickname || auth.user?.username}</Typography.Text>
                                </Space>
                            </Button>
                        </Dropdown>
                    </Space>
                </Header>
                <Content className="admin-content">
                    {canAccessCurrentPath ? (
                        <Outlet/>
                    ) : (
                        <Result
                            status="403"
                            subTitle="当前账号没有该菜单权限。"
                            title="无权访问"
                        />
                    )}
                </Content>
            </Layout>
        </Layout>
    )
}
