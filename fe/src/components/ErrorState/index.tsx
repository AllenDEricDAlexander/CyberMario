import {ReloadOutlined} from '@ant-design/icons'
import {Button, Result, Typography} from 'antd'
import type {ReactNode} from 'react'

type ErrorStateProps = {
    message: string
    onRetry?: () => void
    title?: string
    /** Extra buttons rendered next to the retry action. */
    extra?: ReactNode
    /** Tighter padding for use inside cards, drawers and split panels. */
    inline?: boolean
}

export function ErrorState({message, onRetry, title = '请求失败', extra, inline}: ErrorStateProps) {
    if (inline) {
        return (
            <div className="state-block state-block-inline">
                <div>
                    <Typography.Text strong type="danger">{title}</Typography.Text>
                    <Typography.Paragraph className="state-block-description">
                        {message}
                    </Typography.Paragraph>
                    {(onRetry || extra) && (
                        <div className="state-block-actions">
                            {onRetry && (
                                <Button icon={<ReloadOutlined/>} onClick={onRetry} size="small">重试</Button>
                            )}
                            {extra}
                        </div>
                    )}
                </div>
            </div>
        )
    }

    return (
        <Result
            extra={(onRetry || extra) && (
                <>
                    {onRetry && (
                        <Button icon={<ReloadOutlined/>} onClick={onRetry} type="primary">
                            重试
                        </Button>
                    )}
                    {extra}
                </>
            )}
            status="error"
            subTitle={message}
            title={title}
        />
    )
}
