import {Tag} from 'antd'
import {useLocation} from 'react-router'
import {EmptyState} from '../../../components/EmptyState'
import {PageToolbar} from '../../../components/PageToolbar'
import {NutritionStack} from '../NutritionPageLayout'

const nutritionRouteTitles: Record<string, string> = {
    '/nutrition/home': '营养首页',
    '/nutrition/families': '家庭营养',
    '/nutrition/members': '成员健康',
    '/nutrition/recipes': '家庭菜谱',
    '/nutrition/ai-menus': 'AI 菜单',
    '/nutrition/confirmations': '用餐确认',
    '/nutrition/meal-summary': '餐食汇总',
    '/nutrition/shopping': '采购清单',
    '/nutrition/budget': '预算分析',
    '/nutrition/records': '营养记录',
    '/nutrition/platform': '营养平台',
}

function NutritionPlaceholderPage() {
    const location = useLocation()
    const title = nutritionRouteTitles[location.pathname] ?? '营养管理'
    return (
        <NutritionStack>
            <PageToolbar eyebrow={<Tag color="processing">MVP</Tag>} title={title}/>
            <div className="state-card">
                <EmptyState
                    description="页面业务将在后续任务接入；当前模块已提供路由、服务和共享组件基础。"
                    title="该页面还在建设中"
                />
            </div>
        </NutritionStack>
    )
}

export const Component = NutritionPlaceholderPage
