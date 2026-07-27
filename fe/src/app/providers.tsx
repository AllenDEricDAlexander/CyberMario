import {type ReactNode, useEffect, useMemo} from 'react'
import {App, ConfigProvider} from 'antd'
import {XProvider} from '@ant-design/x'
import zhCN from 'antd/locale/zh_CN'
import zhCNX from '@ant-design/x/locale/zh_CN'
import {AuthProvider} from '../modules/auth/authStore'
import {buildAntdTheme} from '../theme/antdTheme'
import {ThemeModeProvider, useThemeMode} from '../theme/themeMode'
import {GlobalErrorProvider, reportGlobalError} from './globalError'
import {registerAsyncErrorHandler, registerUnhandledRejectionReporter} from '../utils/async'

type AppProvidersProps = {
    children: ReactNode
}

export function AppProviders({children}: AppProvidersProps) {
    return (
        <ThemeModeProvider>
            <ThemedProviders>{children}</ThemedProviders>
        </ThemeModeProvider>
    )
}

function ThemedProviders({children}: AppProvidersProps) {
    const {mode, density} = useThemeMode()
    const theme = useMemo(() => buildAntdTheme(mode, density), [density, mode])
    const xLocale = useMemo(() => ({...zhCN, ...zhCNX}), [])

    return (
        <ConfigProvider
            componentSize={density === 'compact' ? 'small' : 'middle'}
            locale={zhCN}
            theme={theme}
        >
            <XProvider locale={xLocale} theme={theme}>
                <App
                    message={{maxCount: 3, top: 72}}
                    notification={{placement: 'topRight', top: 72}}
                >
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
