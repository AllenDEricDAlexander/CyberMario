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

    // Positioning lives in `.global-error-alert-popup` (styles/components.css)
    // so the banner picks up the active theme.
    return (
        <div className="global-error-alert-popup">
            <Alert
                closable={{closeIcon: true, onClose}}
                showIcon
                title={message}
                type="error"
            />
        </div>
    )
}
