import type {TransferProps} from 'antd'
import {Transfer} from 'antd'
import {useEffect, useState} from 'react'
import {EmptyState} from '../../../components/EmptyState'
import {FormDrawer} from '../../../components/FormDrawer'
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
        <FormDrawer
            description={role?.roleCode}
            footerHint={`已继承 ${targetKeys.length} 个角色`}
            loading={saving}
            onClose={onClose}
            onSubmit={() => void onSubmit(targetKeys.map(Number))}
            open={open}
            size="lg"
            title={`角色继承：${role?.roleName ?? ''}`}
        >
            {dataSource.length ? (
                <Transfer<RoleTransferItem>
                    className="drawer-transfer"
                    dataSource={dataSource}
                    onChange={(nextTargetKeys: TransferProps['targetKeys']) => setTargetKeys((nextTargetKeys ?? []).map(String))}
                    render={(item) => item.title}
                    targetKeys={targetKeys}
                    titles={['可继承角色', '已继承角色']}
                />
            ) : (
                <EmptyState
                    description="当前只有这一个角色，先创建其他角色后才能配置继承关系。"
                    inline
                    title="暂无可继承的角色"
                />
            )}
        </FormDrawer>
    )
}
