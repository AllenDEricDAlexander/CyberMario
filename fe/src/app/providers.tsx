import type {ReactNode} from 'react'
import {App, ConfigProvider} from 'antd'
import zhCN from 'antd/locale/zh_CN'
import {AuthProvider} from '../modules/auth/authStore'

type AppProvidersProps = {
    children: ReactNode
}

export function AppProviders({children}: AppProvidersProps) {
    return (
        <ConfigProvider
            locale={zhCN}
            theme={{
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
            }}
        >
            <App>
                <AuthProvider>{children}</AuthProvider>
            </App>
        </ConfigProvider>
    )
}
