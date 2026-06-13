import {createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useState} from 'react'
import {clearTokens, hasStoredToken, saveTokens} from '../../services/tokenStorage'
import type {MenuTreeResponse, UserResponse} from '../rbac/rbacTypes'
import {fetchCurrentUser, login as loginRequest, logout as logoutRequest} from './authService'
import type {LoginRequest, LoginResponse} from './authTypes'

type AuthState = {
    bootstrapping: boolean
    authenticated: boolean
    user?: UserResponse
    roleCodes: string[]
    menus: MenuTreeResponse[]
    buttonCodes: string[]
    permissionCodes: string[]
    login: (request: LoginRequest) => Promise<void>
    logout: () => Promise<void>
    reload: () => Promise<void>
    hasButton: (buttonCode: string) => boolean
    hasPermission: (permissionCode: string) => boolean
}

const AuthContext = createContext<AuthState | null>(null)

type AuthProviderProps = {
    children: ReactNode
}

export function AuthProvider({children}: AuthProviderProps) {
    const [bootstrapping, setBootstrapping] = useState(true)
    const [session, setSession] = useState<LoginResponse | null>(null)

    const applySession = useCallback((response: LoginResponse) => {
        saveTokens(response)
        setSession(response)
    }, [])

    const reload = useCallback(async () => {
        const response = await fetchCurrentUser()
        applySession(response)
    }, [applySession])

    useEffect(() => {
        if (!hasStoredToken()) {
            setBootstrapping(false)
            return
        }

        reload()
            .catch(() => {
                clearTokens()
                setSession(null)
            })
            .finally(() => {
                setBootstrapping(false)
            })
    }, [reload])

    const login = useCallback(
        async (request: LoginRequest) => {
            const response = await loginRequest(request)
            applySession(response)
        },
        [applySession],
    )

    const logout = useCallback(async () => {
        try {
            await logoutRequest()
        } finally {
            clearTokens()
            setSession(null)
        }
    }, [])

    const buttonCodeSet = useMemo(() => new Set(session?.buttonCodes ?? []), [session?.buttonCodes])
    const permissionCodeSet = useMemo(() => new Set(session?.permissionCodes ?? []), [session?.permissionCodes])

    const value = useMemo<AuthState>(
        () => ({
            bootstrapping,
            authenticated: Boolean(session?.user),
            user: session?.user,
            roleCodes: session?.roleCodes ?? [],
            menus: session?.menus ?? [],
            buttonCodes: session?.buttonCodes ?? [],
            permissionCodes: session?.permissionCodes ?? [],
            login,
            logout,
            reload,
            hasButton: (buttonCode) => buttonCodeSet.has(buttonCode),
            hasPermission: (permissionCode) => permissionCodeSet.has(permissionCode),
        }),
        [bootstrapping, buttonCodeSet, login, logout, permissionCodeSet, reload, session],
    )

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
    const context = useContext(AuthContext)
    if (!context) {
        throw new Error('useAuth must be used inside AuthProvider')
    }
    return context
}
