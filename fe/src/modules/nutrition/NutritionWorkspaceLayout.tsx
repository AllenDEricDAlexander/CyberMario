import {Tabs} from 'antd'
import {Outlet, useLocation, useNavigate} from 'react-router'

const workspaceTabs = [
    {key: '/nutrition/home', label: '营养首页'},
    {key: '/nutrition/families', label: '家庭管理'},
    {key: '/nutrition/members', label: '成员健康'},
    {key: '/nutrition/recipes', label: '家庭菜谱'},
    {key: '/nutrition/ai-menus', label: 'AI 菜单'},
    {key: '/nutrition/confirmations', label: '用餐确认'},
    {key: '/nutrition/meal-summary', label: '餐食汇总'},
    {key: '/nutrition/shopping', label: '采购清单'},
    {key: '/nutrition/budget', label: '预算分析'},
    {key: '/nutrition/records', label: '营养记录'},
]

export function NutritionWorkspaceLayout() {
    const location = useLocation()
    const navigate = useNavigate()
    const activeKey = workspaceTabs.find(({key}) => (
        location.pathname === key || location.pathname.startsWith(`${key}/`)
    ))?.key

    return (
        <>
            <nav aria-label="家庭营养导航">
                <Tabs
                    activeKey={activeKey}
                    items={workspaceTabs}
                    onChange={(path) => void navigate(path)}
                    tabBarStyle={{marginBottom: 16}}
                />
            </nav>
            <Outlet/>
        </>
    )
}
