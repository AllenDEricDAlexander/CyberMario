import {Descriptions, Drawer, Space, Tag} from 'antd'
import type {EffectivePermissionResponse, UserResponse} from '../rbacTypes'

type UserPermissionDrawerProps = {
    open: boolean
    user?: UserResponse | null
    value?: EffectivePermissionResponse | null
    onClose: () => void
}

export function UserPermissionDrawer({open, user, value, onClose}: UserPermissionDrawerProps) {
    return (
        <Drawer onClose={onClose} open={open} title={`有效权限：${user?.username ?? ''}`} width={720}>
            <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="角色">
                    <TagList values={value?.roleCodes}/>
                </Descriptions.Item>
                <Descriptions.Item label="菜单权限">
                    <TagList values={value?.menuCodes}/>
                </Descriptions.Item>
                <Descriptions.Item label="按钮权限">
                    <TagList values={value?.buttonCodes}/>
                </Descriptions.Item>
                <Descriptions.Item label="API 权限">
                    <TagList values={value?.apiCodes}/>
                </Descriptions.Item>
            </Descriptions>
        </Drawer>
    )
}

function TagList({values = []}: { values?: string[] }) {
    if (!values.length) {
        return <span>-</span>
    }
    return (
        <Space wrap>
            {values.map((value) => (
                <Tag key={value}>{value}</Tag>
            ))}
        </Space>
    )
}
