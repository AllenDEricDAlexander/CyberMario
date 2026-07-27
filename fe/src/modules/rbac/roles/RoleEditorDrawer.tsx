import {Form, Input, InputNumber, Select, Switch} from 'antd'
import {useEffect} from 'react'
import {FormDrawer} from '../../../components/FormDrawer'
import {voidify} from '../../../utils/async'
import {RBAC_STATUS_OPTIONS} from '../rbacEnums'
import type {CreateRoleRequest, RoleResponse, UpdateRoleRequest} from '../rbacTypes'

type RoleEditorDrawerProps = {
    open: boolean
    loading?: boolean
    value?: RoleResponse | null
    onClose: () => void
    onSubmit: (request: CreateRoleRequest | UpdateRoleRequest) => Promise<void>
}

export function RoleEditorDrawer({open, loading, value, onClose, onSubmit}: RoleEditorDrawerProps) {
    const [form] = Form.useForm<CreateRoleRequest>()
    const editing = Boolean(value)

    useEffect(() => {
        if (!open) return
        form.setFieldsValue({
            roleCode: value?.roleCode ?? '',
            roleName: value?.roleName ?? '',
            status: typeof value?.status === 'object' ? value.status.code === 1 ? 'ENABLED' : 'DISABLED' : value?.status ?? 'ENABLED',
            sortNo: value?.sortNo ?? 0,
            builtIn: value?.builtIn ?? false,
            description: value?.description,
        })
    }, [form, open, value])

    async function handleFinish(values: CreateRoleRequest) {
        await onSubmit(values)
        form.resetFields()
    }

    return (
        <FormDrawer
            description={editing ? value?.roleCode : '角色编码创建后不可修改。'}
            formId="role-editor-form"
            loading={loading}
            onClose={onClose}
            open={open}
            size="md"
            title={editing ? '编辑角色' : '新建角色'}
        >
            <Form form={form} id="role-editor-form" layout="vertical" onFinish={voidify(handleFinish)}
                  requiredMark={false}>
                <Form.Item label="角色编码" name="roleCode" rules={[{required: true, message: '请输入角色编码'}]}>
                    <Input disabled={editing}/>
                </Form.Item>
                <Form.Item label="角色名称" name="roleName" rules={[{required: true, message: '请输入角色名称'}]}>
                    <Input/>
                </Form.Item>
                <Form.Item label="状态" name="status">
                    <Select options={RBAC_STATUS_OPTIONS}/>
                </Form.Item>
                <Form.Item label="排序" name="sortNo">
                    <InputNumber className="u-full-width"/>
                </Form.Item>
                <Form.Item label="内置角色" name="builtIn" valuePropName="checked">
                    <Switch/>
                </Form.Item>
                <Form.Item label="描述" name="description">
                    <Input.TextArea rows={3}/>
                </Form.Item>
            </Form>
        </FormDrawer>
    )
}
