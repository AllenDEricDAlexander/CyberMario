import {Button, Result} from 'antd'

type ErrorStateProps = {
    message: string
    onRetry?: () => void
}

export function ErrorState({message, onRetry}: ErrorStateProps) {
    return (
        <Result
            extra={onRetry && (
                <Button onClick={onRetry} type="primary">
                    重试
                </Button>
            )}
            status="error"
            subTitle={message}
            title="请求失败"
        />
    )
}
