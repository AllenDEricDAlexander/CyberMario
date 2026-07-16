import {
    createContext,
    type ReactNode,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useRef,
    useState,
} from 'react'
import type {InvestmentLoadState} from '../types/investmentCommonTypes'
import type {
    InvestmentPaperAccountSelection,
    InvestmentWorkspaceResponse,
} from '../types/investmentWorkspaceTypes'
import {
    createInvestmentWorkspace,
    listInvestmentWorkspaces,
} from '../services/investmentWorkspaceService'

export type InvestmentWorkspaceState = {
    workspaces: InvestmentWorkspaceResponse[]
    currentWorkspace: InvestmentWorkspaceResponse | null
    currentPaperAccount: InvestmentPaperAccountSelection | null
    loadState: InvestmentLoadState
    loadError?: string
    creating: boolean
    refreshWorkspaces: () => Promise<void>
    createWorkspace: (name: string) => Promise<InvestmentWorkspaceResponse>
    selectWorkspace: (workspaceId: number | null) => void
    setCurrentPaperAccount: (account: InvestmentPaperAccountSelection | null) => void
}

const InvestmentWorkspaceContext = createContext<InvestmentWorkspaceState | null>(null)

export function InvestmentWorkspaceProvider({children}: {children: ReactNode}) {
    const [workspaces, setWorkspaces] = useState<InvestmentWorkspaceResponse[]>([])
    const [currentWorkspaceId, setCurrentWorkspaceId] = useState<number | null>(null)
    const [currentPaperAccount, setCurrentPaperAccount] = useState<InvestmentPaperAccountSelection | null>(null)
    const [loadState, setLoadState] = useState<InvestmentLoadState>('idle')
    const [loadError, setLoadError] = useState<string>()
    const [creating, setCreating] = useState(false)
    const mountedRef = useRef(false)
    const refreshGenerationRef = useRef(0)
    const createGenerationRef = useRef(0)

    const refreshWorkspaces = useCallback(async () => {
        const generation = ++refreshGenerationRef.current
        setLoadState('loading')
        setLoadError(undefined)
        try {
            const page = await listInvestmentWorkspaces(1, 100)
            if (!mountedRef.current || generation !== refreshGenerationRef.current) {
                return
            }
            setWorkspaces(page.records)
            setCurrentWorkspaceId((currentId) => {
                if (currentId === null || page.records.some(({id}) => id === currentId)) {
                    return currentId
                }
                setCurrentPaperAccount(null)
                return null
            })
            setLoadState(page.records.length === 0 ? 'empty' : 'ready')
        } catch (reason) {
            if (!mountedRef.current || generation !== refreshGenerationRef.current) {
                return
            }
            setLoadState(isForbidden(reason) ? 'forbidden' : 'error')
            setLoadError(errorMessage(reason, '投资工作区加载失败'))
        }
    }, [])

    useEffect(() => {
        mountedRef.current = true
        void refreshWorkspaces()
        return () => {
            mountedRef.current = false
            refreshGenerationRef.current += 1
            createGenerationRef.current += 1
        }
    }, [refreshWorkspaces])

    const selectWorkspace = useCallback((workspaceId: number | null) => {
        const selectedId = workspaceId !== null && workspaces.some(({id}) => id === workspaceId)
            ? workspaceId
            : null
        setCurrentWorkspaceId((currentId) => {
            if (currentId !== selectedId) {
                setCurrentPaperAccount(null)
            }
            return selectedId
        })
    }, [workspaces])

    const createWorkspace = useCallback(async (name: string) => {
        const normalizedName = name.trim()
        if (!normalizedName) {
            throw new Error('工作区名称不能为空')
        }
        refreshGenerationRef.current += 1
        const generation = ++createGenerationRef.current
        setCreating(true)
        try {
            const created = await createInvestmentWorkspace({name: normalizedName})
            if (mountedRef.current && generation === createGenerationRef.current) {
                refreshGenerationRef.current += 1
                setWorkspaces((current) => [created, ...current.filter(({id}) => id !== created.id)])
                setCurrentWorkspaceId(created.id)
                setCurrentPaperAccount(null)
                setLoadState('ready')
                setLoadError(undefined)
            }
            return created
        } finally {
            if (mountedRef.current && generation === createGenerationRef.current) {
                setCreating(false)
            }
        }
    }, [])

    const currentWorkspace = useMemo(
        () => workspaces.find(({id}) => id === currentWorkspaceId) ?? null,
        [currentWorkspaceId, workspaces],
    )
    const value = useMemo<InvestmentWorkspaceState>(() => ({
        workspaces,
        currentWorkspace,
        currentPaperAccount,
        loadState,
        loadError,
        creating,
        refreshWorkspaces,
        createWorkspace,
        selectWorkspace,
        setCurrentPaperAccount,
    }), [
        createWorkspace,
        creating,
        currentPaperAccount,
        currentWorkspace,
        loadError,
        loadState,
        refreshWorkspaces,
        selectWorkspace,
        workspaces,
    ])

    return <InvestmentWorkspaceContext.Provider value={value}>{children}</InvestmentWorkspaceContext.Provider>
}

export function useInvestmentWorkspace() {
    const context = useContext(InvestmentWorkspaceContext)
    if (!context) {
        throw new Error('useInvestmentWorkspace must be used inside InvestmentWorkspaceProvider')
    }
    return context
}

function isForbidden(reason: unknown) {
    return typeof reason === 'object' && reason !== null && 'status' in reason && reason.status === 403
}

function errorMessage(reason: unknown, fallback: string) {
    return reason instanceof Error ? reason.message : fallback
}
