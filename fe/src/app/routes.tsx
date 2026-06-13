import {Spin} from 'antd'
import {createBrowserRouter, Navigate, Outlet, useLocation} from 'react-router'
import {AdminLayout} from '../layouts/AdminLayout'
import {firstAuthorizedMenuPath} from '../layouts/AdminLayout/menu'
import {AuthLayout} from '../layouts/AuthLayout'
import {hasAdminPermissionBypass, useAuth} from '../modules/auth/authStore'
import {LoginPage} from '../modules/auth/pages/LoginPage'
import {ChatPage} from '../modules/chat/pages/ChatPage'

export const router = createBrowserRouter([
    {
        path: '/login',
        element: (
            <AuthLayout>
                <LoginPage/>
            </AuthLayout>
        ),
    },
    {
        path: '/',
        element: <RequireAuth/>,
        children: [
            {
                element: <AdminLayout/>,
                children: [
                    {index: true, element: <DefaultAdminRoute/>},
                    {path: 'chat', element: <ChatPage/>},
                    {path: 'rbac/users', lazy: () => import('../modules/rbac/users/UserListPage')},
                    {path: 'rbac/roles', lazy: () => import('../modules/rbac/roles/RoleListPage')},
                    {path: 'rbac/permissions', lazy: () => import('../modules/rbac/permissions/PermissionListPage')},
                    {path: 'rbac/menus', lazy: () => import('../modules/rbac/menus/MenuTreePage')},
                    {path: 'rbac/buttons', lazy: () => import('../modules/rbac/buttons/ButtonListPage')},
                    {path: 'rbac/apis', lazy: () => import('../modules/rbac/apis/ApiPermissionListPage')},
                ],
            },
        ],
    },
    {
        path: '/403',
        element: <ForbiddenRoute/>,
    },
    {
        path: '*',
        element: <Navigate replace to="/chat"/>,
    },
])

function RequireAuth() {
    const auth = useAuth()
    const location = useLocation()

    if (auth.bootstrapping) {
        return (
            <div className="route-loading">
                <Spin size="large"/>
            </div>
        )
    }

    if (!auth.authenticated) {
        return <Navigate replace state={{from: location}} to="/login"/>
    }

    return <Outlet/>
}

function DefaultAdminRoute() {
    const auth = useAuth()
    return <Navigate replace to={firstAuthorizedMenuPath(auth.menus, hasAdminPermissionBypass(auth)) ?? '/403'}/>
}

function ForbiddenRoute() {
    const auth = useAuth()
    if (!auth.authenticated) {
        return <Navigate replace to="/login"/>
    }
    return <Navigate replace to={firstAuthorizedMenuPath(auth.menus, hasAdminPermissionBypass(auth)) ?? '/login'}/>
}
