import {Descriptions, Space, Tag, Typography} from 'antd'
import {FormDrawer} from '../../../components/FormDrawer'
import type {EffectivePermissionResponse, UserResponse} from '../rbacTypes'

type UserPermissionDrawerProps = {
    open: boolean
    user?: UserResponse | null
    value?: EffectivePermissionResponse | null
    onClose: () => void
}

export function UserPermissionDrawer({open, user, value, onClose}: UserPermissionDrawerProps) {
    return (
        <FormDrawer
            description={user?.username}
            footer={false}
            onClose={onClose}
            open={open}
            size="lg"
            title="有效权限"
        >
            <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="角色">
                    <TagList color="blue" values={value?.roleCodes}/>
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
        </FormDrawer>
    )
}

function TagList({values = [], color}: { values?: string[]; color?: string }) {
    if (!values.length) {
        return <Typography.Text type="secondary">无</Typography.Text>
    }
    return (
        <Space size={[4, 4]} wrap>
            {values.map((value) => (
                <Tag color={color} key={value}>{value}</Tag>
            ))}
        </Space>
    )
}
