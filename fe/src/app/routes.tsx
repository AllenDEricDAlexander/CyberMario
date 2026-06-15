import {Spin} from 'antd'
import {createBrowserRouter, Navigate, Outlet, useLocation} from 'react-router'
import {AdminLayout} from '../layouts/AdminLayout'
import {firstAuthorizedMenuPath} from '../layouts/AdminLayout/menu'
import {AuthLayout} from '../layouts/AuthLayout'
import {hasAdminPermissionBypass, useAuth} from '../modules/auth/authStore'
import {LoginPage} from '../modules/auth/pages/LoginPage'
import {RegisterPage} from '../modules/auth/pages/RegisterPage'
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
        path: '/register',
        element: (
            <AuthLayout>
                <RegisterPage/>
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
                    {path: 'dashboard', lazy: () => import('../modules/dashboard/DashboardPage')},
                    {path: 'account/settings', lazy: () => import('../modules/account/pages/AccountSettingsPage')},
                    {path: 'chat', element: <ChatPage/>},
                    {path: 'agent/debug', lazy: () => import('../modules/agent/AgentDebugPage')},
                    {
                        path: 'agent/conversation-audits',
                        lazy: () => import('../modules/agent/AgentConversationAuditPage')
                    },
                    {path: 'agent/mcp/servers', lazy: () => import('../modules/agent/mcp/McpServerListPage')},
                    {path: 'agent/mcp/tools', lazy: () => import('../modules/agent/mcp/McpToolListPage')},
                    {path: 'agent/mcp/logs', lazy: () => import('../modules/agent/mcp/McpToolCallLogListPage')},
                    {path: 'rag/chat', lazy: () => import('../modules/rag/RagChatPage')},
                    {path: 'rag/knowledge-bases', lazy: () => import('../modules/rag/KnowledgeBaseListPage')},
                    {path: 'rag/documents', lazy: () => import('../modules/rag/DocumentListPage')},
                    {path: 'rag/documents/:documentId', lazy: () => import('../modules/rag/DocumentDetailPage')},
                    {path: 'rag/ingestion-jobs', lazy: () => import('../modules/rag/IngestionJobListPage')},
                    {path: 'rag/retrieval-lab', lazy: () => import('../modules/rag/RetrievalLabPage')},
                    {path: 'rag/arxiv-logs', lazy: () => import('../modules/rag/ArxivToolLogListPage')},
                    {path: 'rag/settings', lazy: () => import('../modules/rag/RagSettingsPage')},
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
    return <Navigate replace
                     to={firstAuthorizedMenuPath(auth.menus, hasAdminPermissionBypass(auth), auth.roleCodes) ?? '/403'}/>
}

function ForbiddenRoute() {
    const auth = useAuth()
    if (!auth.authenticated) {
        return <Navigate replace to="/login"/>
    }
    return <Navigate replace
                     to={firstAuthorizedMenuPath(auth.menus, hasAdminPermissionBypass(auth), auth.roleCodes) ?? '/login'}/>
}
