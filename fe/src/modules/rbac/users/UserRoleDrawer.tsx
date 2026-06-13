import type {TransferProps} from 'antd'
import {Drawer, Transfer} from 'antd'
import {useEffect, useState} from 'react'
import type {RoleResponse, UserResponse} from '../rbacTypes'

type UserRoleDrawerProps = {
    open: boolean
    user?: UserResponse | null
    roles: RoleResponse[]
    selectedRoleIds: number[]
    saving?: boolean
    onClose: () => void
    onSubmit: (ids: number[]) => Promise<void>
}

type RoleTransferItem = {
    key: string
    title: string
    description?: string
}

export function UserRoleDrawer({
                                   open,
                                   user,
                                   roles,
                                   selectedRoleIds,
                                   saving,
                                   onClose,
                                   onSubmit,
                               }: UserRoleDrawerProps) {
    const [targetKeys, setTargetKeys] = useState<string[]>([])

    useEffect(() => {
        if (open) {
            setTargetKeys(selectedRoleIds.map(String))
        }
    }, [open, selectedRoleIds])

    const dataSource = roles.map<RoleTransferItem>((role) => ({
        key: String(role.id),
        title: `${role.roleName} (${role.roleCode})`,
        description: role.description,
    }))

    return (
        <Drawer
            destroyOnHidden
            extra={<button className="drawer-submit" disabled={saving}
                           onClick={() => onSubmit(targetKeys.map(Number))}>保存</button>}
            onClose={onClose}
            open={open}
            title={`分配角色：${user?.username ?? ''}`}
            width={680}
        >
            <Transfer<RoleTransferItem>
                dataSource={dataSource}
                listStyle={{width: 290, height: 420}}
                onChange={(nextTargetKeys: TransferProps['targetKeys']) => setTargetKeys((nextTargetKeys ?? []).map(String))}
                render={(item) => item.title}
                targetKeys={targetKeys}
                titles={['可选角色', '已分配角色']}
            />
        </Drawer>
    )
}
