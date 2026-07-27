import {Form, Input, Select, Switch} from 'antd'
import {useEffect} from 'react'
import {FormDrawer} from '../../../components/FormDrawer'
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
            status: value
                ? typeof value.status === 'object'
                    ? value.status.code === 1 ? 'ENABLED' : 'DISABLED'
                    : value.status
                : undefined,
            remark: value?.remark,
            locked: value?.locked,
            passwordExpired: value?.passwordExpired,
        })
    }, [form, open, value])

    async function handleFinish(values: UserFormValues) {
        await onSubmit(editing ? values : values)
        form.resetFields()
    }

    return (
        <FormDrawer
            description={editing ? value?.accountNo : '创建后将生成激活链接，由用户自行设置密码'}
            formId="user-editor-form"
            loading={loading}
            onClose={onClose}
            open={open}
            title={editing ? '编辑用户' : '新建用户'}
        >
            <Form form={form} id="user-editor-form" layout="vertical" onFinish={voidify(handleFinish)}
                requiredMark={false}>
                <Form.Item label="账号" name="accountNo" rules={[{required: true, message: '请输入账号'}]}>
                    <Input disabled={editing}/>
                </Form.Item>
                <Form.Item label="用户名" name="username" rules={[{required: true, message: '请输入用户名'}]}>
                    <Input disabled={editing}/>
                </Form.Item>
                <Form.Item label="昵称" name="nickname">
                    <Input/>
                </Form.Item>
                <Form.Item
                    label="邮箱"
                    name="email"
                    rules={[
                        {required: !editing, message: '请输入邮箱'},
                        {type: 'email', message: '请输入有效邮箱'},
                    ]}
                >
                    <Input/>
                </Form.Item>
                <Form.Item label="手机" name="mobile">
                    <Input/>
                </Form.Item>
                <Form.Item label="头像地址" name="avatarUrl">
                    <Input/>
                </Form.Item>
                {editing && (
                    <>
                        <Form.Item label="状态" name="status">
                            <Select options={RBAC_STATUS_OPTIONS}/>
                        </Form.Item>
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
        </FormDrawer>
    )
}
