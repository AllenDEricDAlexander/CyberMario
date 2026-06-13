import {Drawer, Select} from 'antd'
import {useEffect, useState} from 'react'
import type {PermissionResponse} from '../rbacTypes'

type ButtonApiDrawerProps = {
    open: boolean
    button?: PermissionResponse | null
    apiPermissions: PermissionResponse[]
    selectedIds: number[]
    saving?: boolean
    onClose: () => void
    onSubmit: (ids: number[]) => Promise<void>
}

export function ButtonApiDrawer({
                                    open,
                                    button,
                                    apiPermissions,
                                    selectedIds,
                                    saving,
                                    onClose,
                                    onSubmit,
                                }: ButtonApiDrawerProps) {
    const [ids, setIds] = useState<number[]>([])

    useEffect(() => {
        if (open) {
            setIds(selectedIds)
        }
    }, [open, selectedIds])

    return (
        <Drawer
            destroyOnHidden
            extra={<button className="drawer-submit" disabled={saving} onClick={() => onSubmit(ids)}>保存</button>}
            onClose={onClose}
            open={open}
            title={`绑定 API：${button?.permName ?? ''}`}
            width={560}
        >
            <Select
                mode="multiple"
                onChange={setIds}
                optionFilterProp="label"
                options={apiPermissions.map((permission) => ({
                    value: permission.id,
                    label: `${permission.permName} (${permission.permCode})`,
                }))}
                placeholder="选择 API 权限"
                style={{width: '100%'}}
                value={ids}
            />
        </Drawer>
    )
}
