import {App, Button, Input, Modal, Space, Typography} from 'antd'
import {DateTimeText} from '../../../components/DateTimeText'
import type {ActivationDeliveryResponse} from '../rbacTypes'

type ActivationLinkModalProps = {
    title: string
    value: ActivationDeliveryResponse | null
    onClose: () => void
}

export function ActivationLinkModal({title, value, onClose}: ActivationLinkModalProps) {
    const {message} = App.useApp()
    const url = value?.mockActivationUrl

    async function copyLink() {
        if (!url) return
        await navigator.clipboard.writeText(url)
        message.success('激活链接已复制')
    }

    return (
        <Modal
            destroyOnHidden
            footer={<Button onClick={onClose}>关闭</Button>}
            onCancel={onClose}
            open={Boolean(value)}
            title={title}
        >
            {value && (
                <Space orientation="vertical" size="middle" style={{width: '100%'}}>
                    <Typography.Text>
                        链接失效时间：<DateTimeText value={value.expiresAt}/>
                    </Typography.Text>
                    {url && <Input.TextArea readOnly autoSize value={url}/>}
                    <Button disabled={!url} onClick={() => void copyLink()} type="primary">
                        复制激活链接
                    </Button>
                </Space>
            )}
        </Modal>
    )
}
