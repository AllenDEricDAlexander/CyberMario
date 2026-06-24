import {MoreOutlined} from '@ant-design/icons'
import {Button, Drawer, Dropdown, Popconfirm, Space, Table, Tag} from 'antd'
import type {ColumnsType} from 'antd/es/table'
import type {ClocktowerRoomMemberResponse} from '../clocktowerTypes'

type ClocktowerMemberDrawerProps = {
    open: boolean
    members?: ClocktowerRoomMemberResponse[]
    actionUserId?: number | null
    currentUserId?: number | null
    onClose: () => void
    onKickMember: (userId: number, ban: boolean) => void
}

type ClocktowerMemberTableProps = Omit<ClocktowerMemberDrawerProps, 'open' | 'onClose'>

export function ClocktowerMemberDrawer({
                                           open,
                                           members = [],
                                           actionUserId = null,
                                           currentUserId = null,
                                           onClose,
                                           onKickMember,
                                       }: ClocktowerMemberDrawerProps) {
    return (
        <Drawer destroyOnHidden onClose={onClose} open={open} size={720} title="房间成员">
            <Space orientation="vertical" size="middle" style={{width: '100%'}}>
                <ClocktowerMemberTable
                    actionUserId={actionUserId}
                    currentUserId={currentUserId}
                    members={members}
                    onKickMember={onKickMember}
                />
            </Space>
        </Drawer>
    )
}

export function ClocktowerMemberTable({
                                          members = [],
                                          actionUserId = null,
                                          currentUserId = null,
                                          onKickMember,
                                      }: ClocktowerMemberTableProps) {
    const columns: ColumnsType<ClocktowerRoomMemberResponse> = [
        {title: '用户 ID', dataIndex: 'userId', width: 110},
        {
            title: '成员',
            dataIndex: 'displayName',
            render: (value: string | null | undefined, record) => value || `用户 ${record.userId}`,
        },
        {title: '座位', dataIndex: 'seatNo', width: 90, render: (value: number | null | undefined) => value ?? '-'},
        {title: '类型', dataIndex: 'memberType', width: 120, render: (value: string) => <Tag>{value}</Tag>},
        {
            title: '状态',
            dataIndex: 'status',
            width: 110,
            render: (value: string) => <Tag color={value === 'ACTIVE' ? 'success' : 'default'}>{value}</Tag>,
        },
        {
            title: '操作',
            fixed: 'right',
            width: 96,
            render: (_, record) => {
                if (record.memberType === 'OWNER' || record.userId === currentUserId) {
                    return <Tag>-</Tag>
                }

                return (
                    <Dropdown
                        menu={{
                            items: [
                                {
                                    key: 'kick',
                                    label: (
                                        <Popconfirm
                                            onConfirm={() => onKickMember(record.userId, false)}
                                            title="确认移出该成员？"
                                        >
                                            <span>移出成员</span>
                                        </Popconfirm>
                                    ),
                                },
                                {
                                    danger: true,
                                    key: 'ban',
                                    label: (
                                        <Popconfirm
                                            onConfirm={() => onKickMember(record.userId, true)}
                                            title="确认移出并禁止该成员再次进入？"
                                        >
                                            <span>移出并封禁</span>
                                        </Popconfirm>
                                    ),
                                },
                            ],
                        }}
                        trigger={['click']}
                    >
                        <Button
                            aria-label={`成员 ${record.userId} 操作`}
                            icon={<MoreOutlined/>}
                            loading={actionUserId === record.userId}
                            size="small"
                        />
                    </Dropdown>
                )
            },
        },
    ]

    return (
        <Table
            columns={columns}
            dataSource={members}
            pagination={false}
            rowKey="memberId"
            scroll={{x: 680}}
            size="small"
        />
    )
}
