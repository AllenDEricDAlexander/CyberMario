import {type ReactNode, useEffect} from 'react'
import {App, ConfigProvider} from 'antd'
import {XProvider} from '@ant-design/x'
import zhCN from 'antd/locale/zh_CN'
import zhCNX from '@ant-design/x/locale/zh_CN'
import {AuthProvider} from '../modules/auth/authStore'
import {GlobalErrorProvider, reportGlobalError} from './globalError'
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
                    <GlobalErrorProvider>
                        <AsyncErrorReporter>
                            <AuthProvider>{children}</AuthProvider>
                        </AsyncErrorReporter>
                    </GlobalErrorProvider>
                </App>
            </XProvider>
        </ConfigProvider>
    )
}

function AsyncErrorReporter({children}: AppProvidersProps) {
    useEffect(() => {
        const disposeRejectedHandler = registerAsyncErrorHandler(reportGlobalError)
        const disposeUnhandledRejectionHandler = registerUnhandledRejectionReporter(reportGlobalError)
        return () => {
            disposeRejectedHandler()
            disposeUnhandledRejectionHandler()
        }
    }, [])

    return <>{children}</>
}
