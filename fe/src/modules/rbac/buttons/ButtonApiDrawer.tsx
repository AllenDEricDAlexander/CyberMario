import {Select} from 'antd'
import {useEffect, useState} from 'react'
import {EmptyState} from '../../../components/EmptyState'
import {FormDrawer} from '../../../components/FormDrawer'
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
        <FormDrawer
            description={button?.permCode}
            footerHint={`已绑定 ${ids.length} 个 API 权限`}
            loading={saving}
            onClose={onClose}
            onSubmit={() => void onSubmit(ids)}
            open={open}
            size="md"
            title={`绑定 API：${button?.permName ?? ''}`}
        >
            {apiPermissions.length ? (
                <Select
                    className="u-full-width"
                    mode="multiple"
                    onChange={setIds}
                    optionFilterProp="label"
                    options={apiPermissions.map((permission) => ({
                        value: permission.id,
                        label: `${permission.permName} (${permission.permCode})`,
                    }))}
                    placeholder="选择 API 权限"
                    value={ids}
                />
            ) : (
                <EmptyState
                    description="请先在“API 权限”页面维护接口权限，之后即可绑定到按钮。"
                    inline
                    title="暂无可绑定的 API 权限"
                />
            )}
        </FormDrawer>
    )
}
