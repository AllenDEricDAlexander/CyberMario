import type {TransferProps} from 'antd'
import {Drawer, Transfer} from 'antd'
import {useEffect, useState} from 'react'
import type {RoleResponse} from '../rbacTypes'

type RoleInheritanceDrawerProps = {
    open: boolean
    role?: RoleResponse | null
    roles: RoleResponse[]
    selectedRoleIds: number[]
    saving?: boolean
    onClose: () => void
    onSubmit: (ids: number[]) => Promise<void>
}

type RoleTransferItem = {
    key: string
    title: string
}

export function RoleInheritanceDrawer({
                                          open,
                                          role,
                                          roles,
                                          selectedRoleIds,
                                          saving,
                                          onClose,
                                          onSubmit,
                                      }: RoleInheritanceDrawerProps) {
    const [targetKeys, setTargetKeys] = useState<string[]>([])

    useEffect(() => {
        if (open) {
            setTargetKeys(selectedRoleIds.map(String))
        }
    }, [open, selectedRoleIds])

    const dataSource = roles
        .filter((item) => item.id !== role?.id)
        .map<RoleTransferItem>((item) => ({
            key: String(item.id),
            title: `${item.roleName} (${item.roleCode})`,
        }))

    return (
        <Drawer
            destroyOnHidden
            extra={<button className="drawer-submit" disabled={saving}
                           onClick={() => onSubmit(targetKeys.map(Number))}>保存</button>}
            onClose={onClose}
            open={open}
            title={`角色继承：${role?.roleName ?? ''}`}
            width={680}
        >
            <Transfer<RoleTransferItem>
                dataSource={dataSource}
                listStyle={{width: 290, height: 420}}
                onChange={(nextTargetKeys: TransferProps['targetKeys']) => setTargetKeys((nextTargetKeys ?? []).map(String))}
                render={(item) => item.title}
                targetKeys={targetKeys}
                titles={['可继承角色', '已继承角色']}
            />
        </Drawer>
    )
}
