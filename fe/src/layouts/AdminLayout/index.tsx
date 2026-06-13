import {LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, ReloadOutlined, UserOutlined} from '@ant-design/icons'
import {Avatar, Button, Dropdown, Layout, Menu, Space, Typography} from 'antd'
import {useMemo, useState} from 'react'
import {Outlet, useLocation, useNavigate} from 'react-router'
import {useAuth} from '../../modules/auth/authStore'
import {adminMenuItems, findMenuPath} from './menu'

const {Header, Sider, Content} = Layout

export function AdminLayout() {
    const auth = useAuth()
    const location = useLocation()
    const navigate = useNavigate()
    const [collapsed, setCollapsed] = useState(false)

    const selectedKeys = useMemo(() => {
        const matched = flattenMenuKeys().find((key) => location.pathname === key || location.pathname.startsWith(`${key}/`))
        return matched ? [matched] : ['/chat']
    }, [location.pathname])

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
                    items={adminMenuItems}
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
                    <Outlet/>
                </Content>
            </Layout>
        </Layout>
    )
}

function flattenMenuKeys() {
    return ['/chat', '/rbac/users', '/rbac/roles', '/rbac/permissions', '/rbac/menus', '/rbac/buttons', '/rbac/apis']
}
