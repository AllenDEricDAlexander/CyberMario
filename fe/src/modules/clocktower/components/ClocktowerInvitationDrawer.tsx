import {SendOutlined} from '@ant-design/icons'
import {Button, Drawer, Form, InputNumber, Select, Space, Table, Tag} from 'antd'
import type {FormInstance} from 'antd/es/form'
import type {ColumnsType} from 'antd/es/table'
import {useEffect} from 'react'
import type {
    ClocktowerRoomInvitationCreateRequest,
    ClocktowerRoomReservationResponse,
} from '../clocktowerTypes'

type ClocktowerInvitationDrawerProps = {
    open: boolean
    maxSeatNo: number
    reservations?: ClocktowerRoomReservationResponse[]
    loading?: boolean
    onClose: () => void
    onSubmit: (request: ClocktowerRoomInvitationCreateRequest) => void
}

const invitationTypeOptions = [
    {label: '房间邀请', value: 'ROOM'},
    {label: '座位邀请', value: 'SEAT'},
]

type ClocktowerInvitationDrawerContentProps = {
    form?: FormInstance<ClocktowerRoomInvitationCreateRequest>
    maxSeatNo: number
    reservations?: ClocktowerRoomReservationResponse[]
}

type ClocktowerInvitationDrawerFooterProps = {
    loading?: boolean
    onClose: () => void
    onSubmit: () => void
}

export function ClocktowerInvitationDrawer({
                                               open,
                                               maxSeatNo,
                                               reservations = [],
                                               loading = false,
                                               onClose,
                                               onSubmit,
                                           }: ClocktowerInvitationDrawerProps) {
    const [form] = Form.useForm<ClocktowerRoomInvitationCreateRequest>()

    useEffect(() => {
        if (open) {
            form.setFieldsValue({invitationType: 'SEAT', expiresAt: null})
        }
    }, [form, open])

    async function submit() {
        const values = await form.validateFields()
        onSubmit({
            inviteeUserId: values.inviteeUserId,
            invitationType: values.invitationType,
            targetSeatNo: values.targetSeatNo ?? null,
            expiresAt: null,
        })
    }

    return (
        <Drawer
            destroyOnHidden
            footer={
                <ClocktowerInvitationDrawerFooter
                    loading={loading}
                    onClose={onClose}
                    onSubmit={() => void submit()}
                />
            }
            onClose={onClose}
            open={open}
            size={560}
            title="邀请玩家"
        >
            <ClocktowerInvitationDrawerContent form={form} maxSeatNo={maxSeatNo} reservations={reservations}/>
        </Drawer>
    )
}

export function ClocktowerInvitationDrawerContent({
                                                     form,
                                                     maxSeatNo,
                                                     reservations = [],
                                                 }: ClocktowerInvitationDrawerContentProps) {
    const columns: ColumnsType<ClocktowerRoomReservationResponse> = [
        {title: '邀请 ID', dataIndex: 'invitationId', width: 120},
        {title: '受邀用户', dataIndex: 'inviteeUserId', width: 120},
        {
            title: '预留座位',
            dataIndex: 'targetSeatNo',
            width: 120,
            render: (value: number | null | undefined) => value ? <Tag color="warning">#{value}</Tag> : '-',
        },
        {title: '过期时间', dataIndex: 'expiresAt', render: (value: string | null | undefined) => value ?? '-'},
    ]

    return (
        <>
            <Form form={form} layout="vertical">
                <Form.Item
                    label="受邀用户 ID"
                    name="inviteeUserId"
                    rules={[{required: true, message: '请输入受邀用户 ID'}]}
                >
                    <InputNumber min={1} precision={0} style={{width: '100%'}}/>
                </Form.Item>
                <Form.Item
                    label="邀请类型"
                    name="invitationType"
                    rules={[{required: true, message: '请选择邀请类型'}]}
                >
                    <Select options={invitationTypeOptions}/>
                </Form.Item>
                <Form.Item label="目标座位" name="targetSeatNo">
                    <InputNumber min={1} max={maxSeatNo} precision={0} style={{width: '100%'}}/>
                </Form.Item>
            </Form>
            <Table
                columns={columns}
                dataSource={reservations}
                pagination={false}
                rowKey="invitationId"
                size="small"
            />
        </>
    )
}

export function ClocktowerInvitationDrawerFooter({
                                                    loading = false,
                                                    onClose,
                                                    onSubmit,
                                                }: ClocktowerInvitationDrawerFooterProps) {
    return (
        <Space style={{display: 'flex', justifyContent: 'end'}}>
            <Button onClick={onClose}>取消</Button>
            <Button icon={<SendOutlined/>} loading={loading} onClick={onSubmit} type="primary">
                创建邀请
            </Button>
        </Space>
    )
}
