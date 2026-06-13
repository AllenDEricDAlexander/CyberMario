import {Button, Drawer, Form, Input, InputNumber, Select, Switch, TreeSelect} from 'antd'
import {useEffect, useMemo} from 'react'
import {toTreeSelectOptions} from '../../../utils/tree'
import {
    API_MATCHER_TYPE_OPTIONS,
    API_RISK_LEVEL_OPTIONS,
    HTTP_METHOD_OPTIONS,
    PERMISSION_STATUS_OPTIONS,
    PERMISSION_TYPE_OPTIONS,
} from '../rbacEnums'
import type {MenuTreeResponse, PermissionRequest, PermissionResponse} from '../rbacTypes'

type PermissionEditorDrawerProps = {
    open: boolean
    loading?: boolean
    title: string
    fixedType?: 'MENU' | 'BUTTON' | 'API'
    value?: PermissionResponse | null
    menus?: MenuTreeResponse[]
    apiPermissions?: PermissionResponse[]
    onClose: () => void
    onSubmit: (request: PermissionRequest) => Promise<void>
}

type PermissionFormValues = {
    permCode: string
    permName: string
    permType: 'MENU' | 'BUTTON' | 'API'
    parentId?: number
    status: 'ENABLED' | 'DISABLED' | 'DRAFT'
    sortNo: number
    description?: string
    parentMenuId?: number
    routeName?: string
    routePath?: string
    component?: string
    redirect?: string
    icon?: string
    hidden?: boolean
    cacheable?: boolean
    externalLink?: string
    menuPermissionId?: number
    buttonKey?: string
    frontendAction?: string
    styleHint?: string
    buttonDescription?: string
    apiPermissionIds?: number[]
    httpMethod?: string
    urlPattern?: string
    matcherType?: 'EXACT' | 'MVC' | 'ANT' | 'REGEX'
    publicFlag?: boolean
    serviceTag?: string
    operationName?: string
    riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
}

export function PermissionEditorDrawer({
                                           open,
                                           loading,
                                           title,
                                           fixedType,
                                           value,
                                           menus = [],
                                           apiPermissions = [],
                                           onClose,
                                           onSubmit,
                                       }: PermissionEditorDrawerProps) {
    const [form] = Form.useForm<PermissionFormValues>()
    const permType = Form.useWatch('permType', form)

    const menuOptions = useMemo(
        () => toTreeSelectOptions(menus, (menu) => menu.permissionId, (menu) => menu.permName),
        [menus],
    )

    useEffect(() => {
        if (!open) {
            return
        }
        form.setFieldsValue(toFormValues(value, fixedType))
    }, [fixedType, form, open, value])

    async function handleFinish(values: PermissionFormValues) {
        await onSubmit(toPermissionRequest(values, fixedType))
        form.resetFields()
    }

    return (
        <Drawer
            destroyOnHidden
            onClose={onClose}
            open={open}
            title={title}
            width={560}
            extra={(
                <Button form="permission-editor-form" htmlType="submit" loading={loading} type="primary">
                    保存
                </Button>
            )}
        >
            <Form
                form={form}
                id="permission-editor-form"
                layout="vertical"
                onFinish={handleFinish}
                requiredMark={false}
            >
                <Form.Item label="权限编码" name="permCode" rules={[{required: true, message: '请输入权限编码'}]}>
                    <Input placeholder="menu:rbac:users"/>
                </Form.Item>
                <Form.Item label="权限名称" name="permName" rules={[{required: true, message: '请输入权限名称'}]}>
                    <Input placeholder="用户管理"/>
                </Form.Item>
                <Form.Item label="权限类型" name="permType" rules={[{required: true, message: '请选择权限类型'}]}>
                    <Select disabled={Boolean(fixedType)} options={PERMISSION_TYPE_OPTIONS}/>
                </Form.Item>
                <Form.Item label="父权限 ID" name="parentId">
                    <InputNumber min={1} style={{width: '100%'}}/>
                </Form.Item>
                <Form.Item label="状态" name="status" rules={[{required: true, message: '请选择状态'}]}>
                    <Select options={PERMISSION_STATUS_OPTIONS}/>
                </Form.Item>
                <Form.Item label="排序" name="sortNo">
                    <InputNumber style={{width: '100%'}}/>
                </Form.Item>
                <Form.Item label="描述" name="description">
                    <Input.TextArea rows={3}/>
                </Form.Item>

                {permType === 'MENU' && (
                    <>
                        <Form.Item label="父菜单" name="parentMenuId">
                            <TreeSelect allowClear treeData={menuOptions}/>
                        </Form.Item>
                        <Form.Item label="路由名称" name="routeName">
                            <Input placeholder="RbacUsers"/>
                        </Form.Item>
                        <Form.Item label="路由路径" name="routePath">
                            <Input placeholder="/rbac/users"/>
                        </Form.Item>
                        <Form.Item label="组件标识" name="component">
                            <Input placeholder="rbac/users"/>
                        </Form.Item>
                        <Form.Item label="重定向" name="redirect">
                            <Input/>
                        </Form.Item>
                        <Form.Item label="图标" name="icon">
                            <Input/>
                        </Form.Item>
                        <Form.Item label="隐藏菜单" name="hidden" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                        <Form.Item label="缓存页面" name="cacheable" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                        <Form.Item label="外链" name="externalLink">
                            <Input/>
                        </Form.Item>
                    </>
                )}

                {permType === 'BUTTON' && (
                    <>
                        <Form.Item label="所属菜单" name="menuPermissionId"
                                   rules={[{required: true, message: '请选择所属菜单'}]}>
                            <TreeSelect treeData={menuOptions}/>
                        </Form.Item>
                        <Form.Item label="按钮 Key" name="buttonKey"
                                   rules={[{required: true, message: '请输入按钮 Key'}]}>
                            <Input placeholder="create"/>
                        </Form.Item>
                        <Form.Item label="前端动作" name="frontendAction">
                            <Input placeholder="rbac:user:create"/>
                        </Form.Item>
                        <Form.Item label="样式提示" name="styleHint">
                            <Input placeholder="primary"/>
                        </Form.Item>
                        <Form.Item label="按钮说明" name="buttonDescription">
                            <Input.TextArea rows={2}/>
                        </Form.Item>
                        <Form.Item label="关联 API 权限" name="apiPermissionIds">
                            <Select
                                mode="multiple"
                                optionFilterProp="label"
                                options={apiPermissions.map((permission) => ({
                                    value: permission.id,
                                    label: `${permission.permName} (${permission.permCode})`,
                                }))}
                            />
                        </Form.Item>
                    </>
                )}

                {permType === 'API' && (
                    <>
                        <Form.Item label="HTTP 方法" name="httpMethod"
                                   rules={[{required: true, message: '请选择 HTTP 方法'}]}>
                            <Select options={HTTP_METHOD_OPTIONS}/>
                        </Form.Item>
                        <Form.Item label="URL 模式" name="urlPattern"
                                   rules={[{required: true, message: '请输入 URL 模式'}]}>
                            <Input placeholder="/api/admin/users/**"/>
                        </Form.Item>
                        <Form.Item label="匹配类型" name="matcherType">
                            <Select options={API_MATCHER_TYPE_OPTIONS}/>
                        </Form.Item>
                        <Form.Item label="公开接口" name="publicFlag" valuePropName="checked">
                            <Switch/>
                        </Form.Item>
                        <Form.Item label="服务标识" name="serviceTag">
                            <Input placeholder="rbac"/>
                        </Form.Item>
                        <Form.Item label="操作名称" name="operationName">
                            <Input placeholder="创建用户"/>
                        </Form.Item>
                        <Form.Item label="风险等级" name="riskLevel">
                            <Select options={API_RISK_LEVEL_OPTIONS}/>
                        </Form.Item>
                    </>
                )}
            </Form>
        </Drawer>
    )
}

function toFormValues(value?: PermissionResponse | null, fixedType?: 'MENU' | 'BUTTON' | 'API'): PermissionFormValues {
    return {
        permCode: value?.permCode ?? '',
        permName: value?.permName ?? '',
        permType: fixedType ?? toPermissionType(value?.permType) ?? 'MENU',
        parentId: value?.parentId,
        status: toPermissionStatus(value?.status) ?? 'ENABLED',
        sortNo: value?.sortNo ?? 0,
        description: value?.description,
        parentMenuId: value?.menu?.parentMenuId,
        routeName: value?.menu?.routeName,
        routePath: value?.menu?.routePath,
        component: value?.menu?.component,
        redirect: value?.menu?.redirect,
        icon: value?.menu?.icon,
        hidden: value?.menu?.hidden ?? false,
        cacheable: value?.menu?.cacheable ?? true,
        externalLink: value?.menu?.externalLink,
        menuPermissionId: value?.button?.menuPermissionId,
        buttonKey: value?.button?.buttonKey,
        frontendAction: value?.button?.frontendAction,
        styleHint: value?.button?.styleHint,
        buttonDescription: value?.button?.description,
        apiPermissionIds: value?.button?.apiPermissionIds ?? [],
        httpMethod: value?.api?.httpMethod,
        urlPattern: value?.api?.urlPattern,
        matcherType: toMatcherType(value?.api?.matcherType) ?? 'EXACT',
        publicFlag: value?.api?.publicFlag ?? false,
        serviceTag: value?.api?.serviceTag,
        operationName: value?.api?.operationName,
        riskLevel: toRiskLevel(value?.api?.riskLevel) ?? 'LOW',
    }
}

function toPermissionRequest(values: PermissionFormValues, fixedType?: 'MENU' | 'BUTTON' | 'API'): PermissionRequest {
    const permType = fixedType ?? values.permType
    return {
        permCode: values.permCode,
        permName: values.permName,
        permType,
        parentId: values.parentId ?? null,
        status: values.status,
        sortNo: values.sortNo,
        description: values.description,
        menu: permType === 'MENU'
            ? {
                parentMenuId: values.parentMenuId,
                routeName: values.routeName,
                routePath: values.routePath,
                component: values.component,
                redirect: values.redirect,
                icon: values.icon,
                hidden: Boolean(values.hidden),
                cacheable: values.cacheable !== false,
                externalLink: values.externalLink,
            }
            : undefined,
        button: permType === 'BUTTON'
            ? {
                menuPermissionId: values.menuPermissionId,
                buttonKey: values.buttonKey,
                frontendAction: values.frontendAction,
                styleHint: values.styleHint,
                description: values.buttonDescription,
                apiPermissionIds: values.apiPermissionIds ?? [],
            }
            : undefined,
        api: permType === 'API'
            ? {
                httpMethod: values.httpMethod,
                urlPattern: values.urlPattern,
                matcherType: values.matcherType ?? 'EXACT',
                publicFlag: Boolean(values.publicFlag),
                serviceTag: values.serviceTag,
                operationName: values.operationName,
                riskLevel: values.riskLevel ?? 'LOW',
            }
            : undefined,
    }
}

function toPermissionType(value: PermissionResponse['permType'] | undefined) {
    if (!value) return undefined
    if (typeof value === 'object') return value.code === 1 ? 'MENU' : value.code === 2 ? 'BUTTON' : 'API'
    return value
}

function toPermissionStatus(value: PermissionResponse['status'] | undefined) {
    if (!value) return undefined
    if (typeof value === 'object') return value.code === 1 ? 'ENABLED' : value.code === 2 ? 'DRAFT' : 'DISABLED'
    return value
}

function toMatcherType(value: NonNullable<PermissionResponse['api']>['matcherType'] | undefined) {
    if (!value) return undefined
    if (typeof value === 'object') {
        return ['EXACT', 'MVC', 'ANT', 'REGEX'][value.code - 1] as 'EXACT' | 'MVC' | 'ANT' | 'REGEX'
    }
    return value
}

function toRiskLevel(value: NonNullable<PermissionResponse['api']>['riskLevel'] | undefined) {
    if (!value) return undefined
    if (typeof value === 'object') {
        return ['LOW', 'MEDIUM', 'HIGH'][value.code - 1] as 'LOW' | 'MEDIUM' | 'HIGH'
    }
    return value
}
