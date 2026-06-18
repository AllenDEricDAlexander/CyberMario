import {Button, Modal, Space} from 'antd'
import type {ReactNode} from 'react'

export type ChatSettingsModalProps = {
    title: ReactNode
    open: boolean
    children: ReactNode
    footer?: ReactNode
    saving?: boolean
    onClose: () => void
    onReset?: () => void
    onSave?: () => void
}

export function ChatSettingsModal(props: ChatSettingsModalProps) {
    const {
        title,
        open,
        children,
        footer,
        saving = false,
        onClose,
        onReset,
        onSave,
    } = props

    const modalFooter = footer ?? (
        onReset || onSave ? (
            <Space>
                {onReset && <Button onClick={onReset}>Reset</Button>}
                {onSave && (
                    <Button loading={saving} type="primary" onClick={onSave}>
                        Save
                    </Button>
                )}
            </Space>
        ) : null
    )

    return (
        <Modal
            className="chat-workspace-x-settings-modal"
            destroyOnHidden
            footer={modalFooter}
            open={open}
            title={title}
            onCancel={onClose}
        >
            {children}
        </Modal>
    )
}
