import {EditOutlined, UserSwitchOutlined} from '@ant-design/icons'
import {Button, Descriptions, Drawer, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {nutritionHealthProfiles, nutritionMembers} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionHealthProfileResponse, NutritionMemberProfileResponse} from './nutritionTypes'

const memberColumns: ColumnsType<NutritionMemberProfileResponse> = [
    {title: '成员', dataIndex: 'nickname'},
    {title: '类型', dataIndex: 'memberType', width: 120, render: (_, record) => <Tag>{record.memberType}</Tag>},
    {title: '登录', dataIndex: 'loginEnabled', width: 100, render: (_, record) => record.loginEnabled ? '允许' : '关闭'},
    {title: '监护人', dataIndex: 'guardianMemberId', width: 120, render: (_, record) => record.guardianMemberId ?? '-'},
    {title: '操作', width: 150, render: () => <Button disabled icon={<EditOutlined/>} size="small">健康档案</Button>},
]

const profileColumns: ColumnsType<NutritionHealthProfileResponse> = [
    {title: '成员 ID', dataIndex: 'memberProfileId', width: 100},
    {title: '活动水平', dataIndex: 'activityLevel'},
    {title: '目标热量', dataIndex: 'targetCalories', width: 120},
    {title: '目标蛋白', dataIndex: 'targetProtein', width: 120},
    {title: '过敏标签', dataIndex: 'allergyTags', render: (tags: string[] = []) => tags.length ? tags.map((tag) => <Tag key={tag} color="error">{tag}</Tag>) : '-'},
]

function MemberHealthPage() {
    const [drawerOpen, setDrawerOpen] = useState(false)

    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button icon={<UserSwitchOutlined/>} onClick={() => setDrawerOpen(true)}>查看监护预览</Button>}
                description="维护家庭成员、健康目标和儿童账号监护关系；当前 Fixture MVP 仅提供监护关系预览。"
                title="成员健康"
            />
            <NutritionPageGrid>
                <NutritionSection title="成员档案">
                    <Table<NutritionMemberProfileResponse>
                        columns={memberColumns}
                        dataSource={nutritionMembers}
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 760}}
                        size="small"
                    />
                </NutritionSection>
                <NutritionSection title="健康档案">
                    <Table<NutritionHealthProfileResponse>
                        columns={profileColumns}
                        dataSource={nutritionHealthProfiles}
                        pagination={false}
                        rowKey="id"
                        scroll={{x: 760}}
                        size="small"
                    />
                </NutritionSection>
            </NutritionPageGrid>
            <Drawer onClose={() => setDrawerOpen(false)} open={drawerOpen} title="监护关系预览">
                <Descriptions column={1} bordered size="small">
                    <Descriptions.Item label="预览状态">只读 Fixture，不会保存变更</Descriptions.Item>
                    <Descriptions.Item label="成员">小米</Descriptions.Item>
                    <Descriptions.Item label="监护关系">由 Mario 代理确认菜单和风险提醒</Descriptions.Item>
                    <Descriptions.Item label="健康目标">目标热量 1600 kcal，花生过敏，低糖</Descriptions.Item>
                </Descriptions>
            </Drawer>
        </NutritionStack>
    )
}

export const Component = MemberHealthPage
