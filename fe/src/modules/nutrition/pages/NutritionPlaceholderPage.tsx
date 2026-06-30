import {Card, Space, Tag, Typography} from 'antd'
import {useLocation} from 'react-router'

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
        <Card>
            <div style={{display: 'flex', flexDirection: 'column', gap: 8}}>
                <Space>
                    <Typography.Title level={3} style={{margin: 0}}>{title}</Typography.Title>
                    <Tag color="processing">MVP</Tag>
                </Space>
                <Typography.Text type="secondary">
                    页面业务将在后续任务接入，当前模块提供路由、服务和共享组件基础。
                </Typography.Text>
            </div>
        </Card>
    )
}

export const Component = NutritionPlaceholderPage
