import {Empty, List, Space, Tag, Typography} from 'antd'
import {enumDesc} from '../../../utils/enum'
import type {ClocktowerNightChecklistResponse} from '../clocktowerTypes'
import {RoleTypeTag} from './RoleTypeTag'

type NightChecklistProps = {
    checklist?: ClocktowerNightChecklistResponse | null
    loading?: boolean
}

export function NightChecklist({checklist, loading}: NightChecklistProps) {
    if (!checklist) {
        return <Empty description="暂无夜晚顺序"/>
    }

    return (
        <Space direction="vertical" size="middle" style={{width: '100%'}}>
            <Space wrap>
                <Tag color="blue">{enumDesc(checklist.nightType)}</Tag>
                <Typography.Text type="secondary">第 {checklist.nightNo} 夜</Typography.Text>
                <Tag color={checklist.completed ? 'success' : 'warning'}>
                    {checklist.completed ? '已完成' : '进行中'}
                </Tag>
            </Space>
            <List
                dataSource={checklist.steps}
                loading={loading}
                locale={{emptyText: <Empty description="暂无夜晚顺序"/>}}
                renderItem={(step) => (
                    <List.Item>
                        <Space wrap>
                            <Tag>{step.orderNo}</Tag>
                            <Typography.Text strong>{step.roleName}</Typography.Text>
                            <Typography.Text type="secondary">{step.roleCode}</Typography.Text>
                            <RoleTypeTag value={step.roleType}/>
                            <Tag color={step.wakeRequired ? 'processing' : 'default'}>
                                {step.wakeRequired ? '需要唤醒' : '跳过'}
                            </Tag>
                            <Tag color={step.completed ? 'success' : 'warning'}>
                                {step.completed ? '已完成' : '待处理'}
                            </Tag>
                            {step.skipReason && <Typography.Text type="secondary">{step.skipReason}</Typography.Text>}
                        </Space>
                    </List.Item>
                )}
            />
        </Space>
    )
}
