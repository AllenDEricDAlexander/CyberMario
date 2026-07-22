import {Button, Drawer, Form, Input, Select, Switch} from 'antd'
import {useEffect} from 'react'
import {voidify} from '../../../utils/async'
import {RBAC_STATUS_OPTIONS} from '../rbacEnums'
import type {CreateUserRequest, UpdateUserRequest, UserResponse} from '../rbacTypes'

type UserEditorDrawerProps = {
    open: boolean
    loading?: boolean
    value?: UserResponse | null
    onClose: () => void
    onSubmit: (request: CreateUserRequest | UpdateUserRequest) => Promise<void>
}

type UserFormValues = CreateUserRequest & {
    initialPassword?: string
    status?: 'ENABLED' | 'DISABLED'
    locked?: boolean
    passwordExpired?: boolean
}

export function UserEditorDrawer({open, loading, value, onClose, onSubmit}: UserEditorDrawerProps) {
    const [form] = Form.useForm<UserFormValues>()
    const editing = Boolean(value)

    useEffect(() => {
        if (!open) return
        form.setFieldsValue({
            accountNo: value?.accountNo ?? '',
            username: value?.username ?? '',
            nickname: value?.nickname,
            email: value?.email,
            mobile: value?.mobile,
            avatarUrl: value?.avatarUrl,
            status: typeof value?.status === 'object' ? value.status.code === 1 ? 'ENABLED' : 'DISABLED' : value?.status ?? 'ENABLED',
            remark: value?.remark,
            locked: value?.locked ?? false,
            passwordExpired: value?.passwordExpired ?? false,
        })
    }, [form, open, value])

    async function handleFinish(values: UserFormValues) {
        await onSubmit(editing ? values : values)
        form.resetFields()
    }

    return (
        <Drawer
            destroyOnHidden
            extra={<Button form="user-editor-form" htmlType="submit" loading={loading} type="primary">保存</Button>}
            onClose={onClose}
            open={open}
            title={editing ? '编辑用户' : '新建用户'}
            width={520}
        >
            <Form form={form} id="user-editor-form" layout="vertical" onFinish={voidify(handleFinish)}
                requiredMark={false}>
                <Form.Item label="账号" name="accountNo" rules={[{required: true, message: '请输入账号'}]}>
                    <Input disabled={editing}/>
                </Form.Item>
                <Form.Item label="用户名" name="username" rules={[{required: true, message: '请输入用户名'}]}>
                    <Input disabled={editing}/>
                </Form.Item>
                {!editing && (
                    <Form.Item label="初始密码" name="initialPassword"
                               rules={[{required: true, message: '请输入初始密码'}]}>
                        <Input.Password/>
                    </Form.Item>
                )}
                <Form.Item label="昵称" name="nickname">
                    <Input/>
                </Form.Item>
                <Form.Item label="邮箱" name="email">
                    <Input/>
                </Form.Item>
                <Form.Item label="手机" name="mobile">
                    <Input/>
                </Form.Item>
                <Form.Item label="头像地址" name="avatarUrl">
                    <Input/>
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select options={RBAC_STATUS_OPTIONS}/>
                </Form.Item>
                {editing && (
                    <>
                        <Form.Item label="锁定" name="locked" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                        <Form.Item label="密码过期" name="passwordExpired" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                    </>
                )}
                <Form.Item label="备注" name="remark">
                    <Input.TextArea rows={3}/>
                </Form.Item>
            </Form>
        </Drawer>
    )
}
