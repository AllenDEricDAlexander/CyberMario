import {Checkbox, Space, Tree} from 'antd'
import type {DataNode} from 'antd/es/tree'
import type {Key} from 'react'
import {useEffect, useMemo, useState} from 'react'
import {EmptyState} from '../../../components/EmptyState'
import {FormDrawer} from '../../../components/FormDrawer'
import {enumDesc} from '../../../utils/enum'
import type {PermissionResponse, RoleResponse} from '../rbacTypes'

type RolePermissionDrawerProps = {
    open: boolean
    role?: RoleResponse | null
    permissions: PermissionResponse[]
    selectedPermissionIds: number[]
    saving?: boolean
    onClose: () => void
    onSubmit: (ids: number[], syncButtonApis: boolean) => Promise<void>
}

export function RolePermissionDrawer({
                                         open,
                                         role,
                                         permissions,
                                         selectedPermissionIds,
                                         saving,
                                         onClose,
                                         onSubmit,
                                     }: RolePermissionDrawerProps) {
    const [checkedKeys, setCheckedKeys] = useState<Key[]>([])
    const [syncButtonApis, setSyncButtonApis] = useState(true)

    useEffect(() => {
        if (open) {
            setCheckedKeys(selectedPermissionIds)
            setSyncButtonApis(true)
        }
    }, [open, selectedPermissionIds])

    const treeData = useMemo<DataNode[]>(
        () => permissions.map((permission) => ({
            key: permission.id,
            title: `${permission.permName} (${permission.permCode}) - ${enumDesc(permission.permType)}`,
        })),
        [permissions],
    )

    return (
        <FormDrawer
            description={role?.roleCode}
            footerHint={`已选 ${checkedKeys.length} 项权限`}
            loading={saving}
            onClose={onClose}
            onSubmit={() => void onSubmit(checkedKeys.map(Number), syncButtonApis)}
            open={open}
            size="lg"
            title={`分配权限：${role?.roleName ?? ''}`}
        >
            <Space className="drawer-inline-toolbar">
                <Checkbox checked={syncButtonApis} onChange={(event) => setSyncButtonApis(event.target.checked)}>
                    同步按钮关联 API
                </Checkbox>
            </Space>
            {treeData.length ? (
                <Tree
                    checkable
                    checkedKeys={checkedKeys}
                    height={520}
                    onCheck={(keys) => setCheckedKeys(Array.isArray(keys) ? keys : keys.checked)}
                    treeData={treeData}
                />
            ) : (
                <EmptyState
                    description="请先在“权限管理”中创建权限，之后即可分配给角色。"
                    inline
                    title="暂无可分配的权限"
                />
            )}
        </FormDrawer>
    )
}
