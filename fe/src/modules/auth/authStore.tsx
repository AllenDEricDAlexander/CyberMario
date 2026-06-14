import {createContext, type ReactNode, useCallback, useContext, useEffect, useMemo, useRef, useState} from 'react'
import {subscribePermissionVersion} from '../../services/permissionVersionEvents'
import {clearTokens, hasStoredToken, saveTokens} from '../../services/tokenStorage'
import type {MenuTreeResponse, UserResponse} from '../rbac/rbacTypes'
import {
    fetchCurrentUser,
    login as loginRequest,
    logout as logoutRequest,
    register as registerRequest
} from './authService'
import type {LoginRequest, LoginResponse, RegisterRequest} from './authTypes'

export type AuthState = {
    bootstrapping: boolean
    authenticated: boolean
    user?: UserResponse
    permissionVersion?: string | null
    permissionChange?: PermissionChangeSnapshot
    roleCodes: string[]
    menus: MenuTreeResponse[]
    buttonCodes: string[]
    permissionCodes: string[]
    login: (request: LoginRequest) => Promise<void>
    register: (request: RegisterRequest) => Promise<void>
    logout: () => Promise<void>
    reload: () => Promise<void>
    hasButton: (buttonCode: string) => boolean
    hasAnyButton: (buttonCodes: string[]) => boolean
    hasPermission: (permissionCode: string) => boolean
}

export type PermissionChangeSnapshot = {
    revision: number
    lostButtonCodes: string[]
}

const AuthContext = createContext<AuthState | null>(null)

type AuthProviderProps = {
    children: ReactNode
}

export function AuthProvider({children}: AuthProviderProps) {
    const [bootstrapping, setBootstrapping] = useState(true)
    const [session, setSession] = useState<LoginResponse | null>(null)
    const [permissionChange, setPermissionChange] = useState<PermissionChangeSnapshot>()
    const sessionRef = useRef<LoginResponse | null>(null)
    const permissionReloadRef = useRef<Promise<void> | null>(null)

    const applySession = useCallback((response: LoginResponse) => {
        const previous = sessionRef.current
        saveTokens(response)
        sessionRef.current = response
        setSession(response)
        if (previous?.permissionVersion && response.permissionVersion
            && previous.permissionVersion !== response.permissionVersion) {
            const nextButtonCodes = new Set(response.buttonCodes ?? [])
            const lostButtonCodes = (previous.buttonCodes ?? [])
                .filter((buttonCode) => !nextButtonCodes.has(buttonCode))
            setPermissionChange((current) => ({
                revision: (current?.revision ?? 0) + 1,
                lostButtonCodes,
            }))
        }
    }, [])

    const reload = useCallback(async () => {
        const response = await fetchCurrentUser()
        applySession(response)
    }, [applySession])

    const reloadPermissionsSilently = useCallback(() => {
        if (permissionReloadRef.current) {
            return permissionReloadRef.current
        }
        permissionReloadRef.current = fetchCurrentUser()
            .then(applySession)
            .finally(() => {
                permissionReloadRef.current = null
            })
        return permissionReloadRef.current
    }, [applySession])

    useEffect(() => {
        if (!hasStoredToken()) {
            setBootstrapping(false)
            return
        }

        reload()
            .catch(() => {
                clearTokens()
                sessionRef.current = null
                setSession(null)
            })
            .finally(() => {
                setBootstrapping(false)
            })
    }, [reload])

    useEffect(() => subscribePermissionVersion((permissionVersion) => {
        const currentSession = sessionRef.current
        if (!currentSession?.user || !permissionVersion || permissionVersion === currentSession.permissionVersion) {
            return
        }
        void reloadPermissionsSilently()
    }), [reloadPermissionsSilently])

    const login = useCallback(
        async (request: LoginRequest) => {
            const response = await loginRequest(request)
            applySession(response)
        },
        [applySession],
    )

    const register = useCallback(
        async (request: RegisterRequest) => {
            const response = await registerRequest(request)
            applySession(response)
        },
        [applySession],
    )

    const logout = useCallback(async () => {
        try {
            await logoutRequest()
        } finally {
            clearTokens()
            sessionRef.current = null
            setSession(null)
            setPermissionChange(undefined)
        }
    }, [])

    const buttonCodeSet = useMemo(() => new Set(session?.buttonCodes ?? []), [session?.buttonCodes])
    const permissionCodeSet = useMemo(() => new Set(session?.permissionCodes ?? []), [session?.permissionCodes])

    const value = useMemo<AuthState>(
        () => ({
            bootstrapping,
            authenticated: Boolean(session?.user),
            user: session?.user,
            permissionVersion: session?.permissionVersion,
            permissionChange,
            roleCodes: session?.roleCodes ?? [],
            menus: session?.menus ?? [],
            buttonCodes: session?.buttonCodes ?? [],
            permissionCodes: session?.permissionCodes ?? [],
            login,
            register,
            logout,
            reload,
            hasButton: (buttonCode) => buttonCodeSet.has(buttonCode),
            hasAnyButton: (buttonCodes) => buttonCodes.some((buttonCode) => buttonCodeSet.has(buttonCode)),
            hasPermission: (permissionCode) => permissionCodeSet.has(permissionCode),
        }),
        [bootstrapping, buttonCodeSet, login, logout, permissionChange, permissionCodeSet, register, reload, session],
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

export function hasAdminPermissionBypass(auth: Pick<AuthState, 'hasPermission' | 'roleCodes'>) {
    return auth.roleCodes.includes('SUPER_ADMIN') || auth.hasPermission('api:rbac:admin:*')
}

export function canUseRbacButton(
    auth: Pick<AuthState, 'hasAnyButton' | 'hasPermission' | 'roleCodes'>,
    buttonCodes: string | string[],
) {
    return hasAdminPermissionBypass(auth) || auth.hasAnyButton(Array.isArray(buttonCodes) ? buttonCodes : [buttonCodes])
}
