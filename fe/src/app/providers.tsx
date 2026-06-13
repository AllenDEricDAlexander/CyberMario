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
                    borderRadius: 6,
                    colorPrimary: '#095e5c',
                },
            }}
        >
            <App>
                <AuthProvider>{children}</AuthProvider>
            </App>
        </ConfigProvider>
    )
}
