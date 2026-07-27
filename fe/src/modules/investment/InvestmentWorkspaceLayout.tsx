import {Button, Flex, Tabs, Typography} from 'antd'
import {useState} from 'react'
import {Outlet, useLocation, useNavigate} from 'react-router'
import {EmptyState} from '../../components/EmptyState'
import {canUseRbacButton, useAuth} from '../auth/authStore'
import {InvestmentAsyncState} from './components/InvestmentAsyncState'
import {InvestmentWorkspaceSelect} from './components/InvestmentWorkspaceSelect'
import {WorkspaceCreateDrawer} from './components/WorkspaceCreateDrawer'
import {
    InvestmentWorkspaceProvider,
    useInvestmentWorkspace,
} from './hooks/useInvestmentWorkspace'
import {investmentButtonCodes} from './investmentPermissionCodes'
import './investment.css'

const workspaceTabs = [
    {key: '/investment/overview', label: '投资总览', requiresWorkspace: true},
    {key: '/investment/market', label: '合约行情', requiresWorkspace: false},
    {key: '/investment/research', label: '分析报告', requiresWorkspace: true},
    {key: '/investment/quant', label: '量化回测', requiresWorkspace: true},
    {key: '/investment/portfolio', label: '模拟盘', requiresWorkspace: true},
    {key: '/investment/agent', label: 'Agent 交易', requiresWorkspace: true},
]

export function InvestmentWorkspaceLayout() {
    return (
        <InvestmentWorkspaceProvider>
            <InvestmentWorkspaceLayoutContent/>
        </InvestmentWorkspaceProvider>
    )
}

function InvestmentWorkspaceLayoutContent() {
    const location = useLocation()
    const navigate = useNavigate()
    const auth = useAuth()
    const [createOpen, setCreateOpen] = useState(false)
    const {
        workspaces,
        currentWorkspace,
        loadState,
        loadError,
        creating,
        refreshWorkspaces,
        createWorkspace,
        selectWorkspace,
    } = useInvestmentWorkspace()
    const isInstrumentDetail = location.pathname.startsWith('/investment/instruments/')
    const activeTab = isInstrumentDetail
        ? workspaceTabs.find(({key}) => key === '/investment/market')
        : workspaceTabs.find(({key}) => location.pathname === key || location.pathname.startsWith(`${key}/`))
    const requiresWorkspace = activeTab?.requiresWorkspace ?? true
    const canCreate = canUseRbacButton(auth, investmentButtonCodes.workspaceCreate)

    return (
        <section className="investment-workspace-shell">
            <header className="investment-workspace-header">
                <div>
                    <Typography.Title level={3}>Investment</Typography.Title>
                    <Typography.Text type="secondary">合约分析、量化研究与模拟交易</Typography.Text>
                </div>
                <Flex align="center" gap={12} wrap>
                    <InvestmentWorkspaceSelect
                        loading={loadState === 'idle' || loadState === 'loading'}
                        onChange={selectWorkspace}
                        value={currentWorkspace?.id}
                        workspaces={workspaces}
                    />
                    {canCreate && <Button onClick={() => setCreateOpen(true)} type="primary">创建工作区</Button>}
                </Flex>
            </header>
            <nav aria-label="投资工作区导航" className="investment-workspace-nav">
                <Tabs
                    activeKey={activeTab?.key}
                    items={workspaceTabs.map(({key, label}) => ({key, label}))}
                    onChange={(path) => void navigate(path)}
                />
            </nav>
            {requiresWorkspace ? (
                <PrivateWorkspaceContent
                    canCreate={canCreate}
                    currentWorkspaceSelected={currentWorkspace !== null}
                    loadError={loadError}
                    loadState={loadState}
                    onCreate={() => setCreateOpen(true)}
                    onRetry={() => void refreshWorkspaces()}
                />
            ) : <Outlet/>}
            <WorkspaceCreateDrawer
                creating={creating}
                onClose={() => setCreateOpen(false)}
                onCreate={createWorkspace}
                open={createOpen}
            />
        </section>
    )
}

function PrivateWorkspaceContent({
    canCreate,
    currentWorkspaceSelected,
    loadError,
    loadState,
    onCreate,
    onRetry,
}: {
    canCreate: boolean
    currentWorkspaceSelected: boolean
    loadError?: string
    loadState: ReturnType<typeof useInvestmentWorkspace>['loadState']
    onCreate: () => void
    onRetry: () => void
}) {
    if (loadState === 'idle' || loadState === 'loading' || loadState === 'error' || loadState === 'forbidden') {
        return (
            <InvestmentAsyncState error={loadError} onRetry={onRetry} state={loadState}>
                <Outlet/>
            </InvestmentAsyncState>
        )
    }
    if (!currentWorkspaceSelected) {
        return (
            <EmptyState
                action={canCreate && <Button onClick={onCreate} type="primary">创建工作区</Button>}
                description="工作区隔离每个人的分析、回测与模拟盘数据；用顶部的选择器挑一个，或新建一个。"
                title="请先选择或创建一个私人投资工作区"
            />
        )
    }
    return <Outlet/>
}
