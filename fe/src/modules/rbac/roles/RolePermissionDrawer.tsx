import {Checkbox, Drawer, Space, Switch, Tree} from 'antd'
import type {DataNode} from 'antd/es/tree'
import type {Key} from 'react'
import {useEffect, useMemo, useState} from 'react'
import {voidify} from '../../../utils/async'
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
        <Drawer
            destroyOnHidden
            extra={<button className="drawer-submit" disabled={saving}
                           onClick={voidify(() => onSubmit(checkedKeys.map(Number), syncButtonApis))}>保存</button>}
            onClose={onClose}
            open={open}
            title={`分配权限：${role?.roleName ?? ''}`}
            width={680}
        >
            <Space className="drawer-inline-toolbar">
                <Checkbox checked={syncButtonApis} onChange={(event) => setSyncButtonApis(event.target.checked)}>
                    同步按钮关联 API
                </Checkbox>
                <Switch checked={syncButtonApis} onChange={setSyncButtonApis}/>
            </Space>
            <Tree
                checkable
                checkedKeys={checkedKeys}
                height={520}
                onCheck={(keys) => setCheckedKeys(Array.isArray(keys) ? keys : keys.checked)}
                treeData={treeData}
            />
        </Drawer>
    )
}
