import {LinkOutlined, SafetyCertificateOutlined} from '@ant-design/icons'
import {Button, Descriptions, Drawer, Form, Input, Select, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import {useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {nutritionClans, nutritionFamilies} from './nutritionPageData'
import {NutritionPageGrid, NutritionSection, NutritionStack} from './NutritionPageLayout'
import type {NutritionClanResponse, NutritionFamilyResponse} from './nutritionTypes'

type ClanFamilyAssociation = {
    id: number
    clanName: string
    familyName: string
    grantScope: string
    status: string
}

const associations: ClanFamilyAssociation[] = [
    {id: 1, clanName: '三代同堂', familyName: '星河家庭', grantScope: '菜单确认、健康查看', status: 'ACTIVE'},
    {id: 2, clanName: '托管亲友圈', familyName: '周末照护家庭', grantScope: '采购清单', status: 'ACTIVE'},
]

const clanColumns: ColumnsType<NutritionClanResponse> = [
    {title: 'Clan 名称', dataIndex: 'name'},
    {title: 'Owner', dataIndex: 'ownerUserId', width: 120},
    {title: '状态', dataIndex: 'status', width: 120, render: (value) => <Tag color="success">{value}</Tag>},
]

const familyColumns: ColumnsType<NutritionFamilyResponse> = [
    {title: '家庭名称', dataIndex: 'name'},
    {title: '地区', dataIndex: 'region', width: 120},
    {title: 'AI', dataIndex: 'aiEnabled', width: 100, render: (value) => value ? '开启' : '关闭'},
]

const associationColumns: ColumnsType<ClanFamilyAssociation> = [
    {title: 'Clan', dataIndex: 'clanName'},
    {title: '家庭', dataIndex: 'familyName'},
    {title: '授权范围', dataIndex: 'grantScope'},
    {title: '状态', dataIndex: 'status', width: 110, render: (value) => <Tag color="success">{value}</Tag>},
]

function ClanFamilyPage() {
    const [grantOpen, setGrantOpen] = useState(false)

    return (
        <NutritionStack>
            <PageToolbar
                actions={<Button icon={<SafetyCertificateOutlined/>} onClick={() => setGrantOpen(true)} type="primary">打开授权</Button>}
                description="管理 Clan、家庭和跨家庭营养协作授权，所有跨家庭访问都需要显式授权。"
                title="家庭营养"
            />
            <NutritionPageGrid>
                <NutritionSection title="Clan 列表">
                    <Table<NutritionClanResponse>
                        columns={clanColumns}
                        dataSource={nutritionClans}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
                <NutritionSection title="家庭列表">
                    <Table<NutritionFamilyResponse>
                        columns={familyColumns}
                        dataSource={nutritionFamilies}
                        pagination={false}
                        rowKey="id"
                        size="small"
                    />
                </NutritionSection>
            </NutritionPageGrid>
            <NutritionSection extra={<Button disabled icon={<LinkOutlined/>} size="small">关联家庭</Button>} title="关联关系">
                <Table<ClanFamilyAssociation>
                    columns={associationColumns}
                    dataSource={associations}
                    pagination={false}
                    rowKey="id"
                    scroll={{x: 760}}
                    size="small"
                />
            </NutritionSection>
            <Drawer
                onClose={() => setGrantOpen(false)}
                open={grantOpen}
                title="显式授权"
            >
                <Form layout="vertical">
                    <Form.Item label="授权家庭" name="familyId">
                        <Select options={nutritionFamilies.map((family) => ({label: family.name, value: family.id}))}/>
                    </Form.Item>
                    <Form.Item label="授权对象" name="grantee">
                        <Input placeholder="输入用户 ID 或 Clan 名称"/>
                    </Form.Item>
                    <Form.Item label="授权范围" name="scope">
                        <Select
                            mode="multiple"
                            options={[
                                {label: '菜单确认', value: 'CONFIRMATION'},
                                {label: '健康查看', value: 'HEALTH_READ'},
                                {label: '采购协作', value: 'SHOPPING'},
                            ]}
                        />
                    </Form.Item>
                </Form>
                <Descriptions column={1} size="small">
                    <Descriptions.Item label="控制方式">显式授权</Descriptions.Item>
                    <Descriptions.Item label="审计要求">保存授权人、被授权人和范围</Descriptions.Item>
                </Descriptions>
            </Drawer>
        </NutritionStack>
    )
}

export const Component = ClanFamilyPage
