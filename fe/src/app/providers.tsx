import {type ReactNode, useEffect} from 'react'
import {App, ConfigProvider} from 'antd'
import {XProvider} from '@ant-design/x'
import zhCN from 'antd/locale/zh_CN'
import zhCNX from '@ant-design/x/locale/zh_CN'
import {resolveErrorMessage} from '../services/request'
import {AuthProvider} from '../modules/auth/authStore'
import {registerAsyncErrorHandler, registerUnhandledRejectionReporter} from '../utils/async'

type AppProvidersProps = {
    children: ReactNode
}

export function AppProviders({children}: AppProvidersProps) {
    const theme = {
        token: {
            borderRadius: 8,
            colorBgContainer: '#ffffff',
            colorBgElevated: '#ffffff',
            colorBgLayout: '#eef7f3',
            colorBorder: '#c8ddd6',
            colorError: '#de496c',
            colorInfo: '#2574d8',
            colorPrimary: '#0f766e',
            colorSuccess: '#16a06f',
            colorText: '#163032',
            colorTextSecondary: '#607477',
            colorWarning: '#d9822b',
            controlHeight: 36,
            fontFamily: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
        },
    }
    const xLocale = {...zhCN, ...zhCNX}

    return (
        <ConfigProvider locale={zhCN} theme={theme}>
            <XProvider locale={xLocale} theme={theme}>
                <App>
                    <AsyncErrorReporter>
                        <AuthProvider>{children}</AuthProvider>
                    </AsyncErrorReporter>
                </App>
            </XProvider>
        </ConfigProvider>
    )
}

function AsyncErrorReporter({children}: AppProvidersProps) {
    const {message} = App.useApp()

    useEffect(() => {
        const reportError = (error: unknown) => {
            message.error(resolveErrorMessage(error))
        }
        const disposeRejectedHandler = registerAsyncErrorHandler(reportError)
        const disposeUnhandledRejectionHandler = registerUnhandledRejectionReporter(reportError)
        return () => {
            disposeRejectedHandler()
            disposeUnhandledRejectionHandler()
        }
    }, [message])

    return <>{children}</>
}
