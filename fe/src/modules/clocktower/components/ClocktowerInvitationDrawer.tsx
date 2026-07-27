import {SendOutlined} from '@ant-design/icons'
import {Button, Form, InputNumber, Select, Tag} from 'antd'
import type {FormInstance} from 'antd/es/form'
import type {ColumnsType} from 'antd/es/table'
import {useEffect} from 'react'
import {DataTable} from '../../../components/DataTable'
import {FormDrawer} from '../../../components/FormDrawer'
import {PageStack} from '../../../components/PageSection'
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
        <FormDrawer
            description="被邀请的玩家会在房间列表中看到邀请，接受后自动入座。"
            footer={
                <ClocktowerInvitationDrawerFooter
                    loading={loading}
                    onClose={onClose}
                    onSubmit={() => void submit()}
                />
            }
            onClose={onClose}
            open={open}
            size="md"
            title="邀请玩家"
        >
            <ClocktowerInvitationDrawerContent form={form} maxSeatNo={maxSeatNo} reservations={reservations}/>
        </FormDrawer>
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
        <PageStack>
            <Form form={form} layout="vertical">
                <Form.Item
                    label="受邀用户 ID"
                    name="inviteeUserId"
                    rules={[{required: true, message: '请输入受邀用户 ID'}]}
                >
                    <InputNumber min={1} precision={0} className="u-full-width"/>
                </Form.Item>
                <Form.Item
                    label="邀请类型"
                    name="invitationType"
                    rules={[{required: true, message: '请选择邀请类型'}]}
                >
                    <Select options={invitationTypeOptions}/>
                </Form.Item>
                <Form.Item label="目标座位" name="targetSeatNo">
                    <InputNumber min={1} max={maxSeatNo} precision={0} className="u-full-width"/>
                </Form.Item>
            </Form>
            <DataTable<ClocktowerRoomReservationResponse>
                columns={columns}
                count={reservations.length}
                dataSource={reservations}
                emptyDescription="创建座位邀请后，为对方预留的座位会显示在这里。"
                emptyTitle="暂无预留座位"
                pagination={false}
                rowKey="invitationId"
                size="small"
                title="待接受的邀请"
            />
        </PageStack>
    )
}

export function ClocktowerInvitationDrawerFooter({
                                                    loading = false,
                                                    onClose,
                                                    onSubmit,
                                                }: ClocktowerInvitationDrawerFooterProps) {
    return (
        <div className="form-drawer-footer">
            <Button disabled={loading} onClick={onClose}>取消</Button>
            <Button icon={<SendOutlined/>} loading={loading} onClick={onSubmit} type="primary">
                创建邀请
            </Button>
        </div>
    )
}
