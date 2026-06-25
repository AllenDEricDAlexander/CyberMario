import { Alert } from 'antd'
import {type ReactNode, useEffect, useState} from 'react'
import {resolveErrorMessage} from '../services/request'

type GlobalErrorHandler = (message: string | undefined) => void

const globalErrorHandlers = new Set<GlobalErrorHandler>()

type GlobalErrorProviderProps = {
    children: ReactNode
}

type GlobalErrorAlertProps = {
    message?: string
    onClose: () => void
}

export function reportGlobalError(error: unknown) {
    const message = typeof error === 'string' ? error : resolveErrorMessage(error)
    globalErrorHandlers.forEach((handler) => handler(message))
}

export function clearGlobalError() {
    globalErrorHandlers.forEach((handler) => handler(undefined))
}

export function registerGlobalErrorHandler(handler: GlobalErrorHandler) {
    globalErrorHandlers.add(handler)
    return () => {
        globalErrorHandlers.delete(handler)
    }
}

export function GlobalErrorProvider({children}: GlobalErrorProviderProps) {
    const [message, setMessage] = useState<string>()

    useEffect(() => registerGlobalErrorHandler(setMessage), [])

    return (
        <>
            <GlobalErrorAlert message={message} onClose={clearGlobalError}/>
            {children}
        </>
    )
}

export function GlobalErrorAlert({message, onClose}: GlobalErrorAlertProps) {
    if (!message) {
        return null
    }

    return (
        <div
            className="global-error-alert-popup"
            style={{
                position: 'fixed',
                top: 24,
                left: '50%',
                transform: 'translateX(-50%)',
                zIndex: 1100,
                width: 'max-content',
                maxWidth: 'calc(100vw - 32px)',
                pointerEvents: 'none',
            }}
        >
            <Alert
                closable={{closeIcon: true, onClose}}
                showIcon
                style={{pointerEvents: 'auto'}}
                title={message}
                type="error"
            />
        </div>
    )
}
