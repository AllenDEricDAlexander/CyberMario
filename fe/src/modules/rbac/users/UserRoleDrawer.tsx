import type {TransferProps} from 'antd'
import {Transfer} from 'antd'
import {useEffect, useState} from 'react'
import {FormDrawer} from '../../../components/FormDrawer'
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
        <FormDrawer
            description={user?.username}
            footerHint={`已分配 ${targetKeys.length} / ${roles.length} 个角色`}
            loading={saving}
            onClose={onClose}
            onSubmit={() => void onSubmit(targetKeys.map(Number))}
            open={open}
            size="lg"
            title="分配角色"
        >
            <Transfer<RoleTransferItem>
                className="role-transfer"
                dataSource={dataSource}
                onChange={(nextTargetKeys: TransferProps['targetKeys']) =>
                    setTargetKeys((nextTargetKeys ?? []).map(String))}
                render={(item) => item.title}
                showSearch
                targetKeys={targetKeys}
                titles={['可选角色', '已分配角色']}
            />
        </FormDrawer>
    )
}
