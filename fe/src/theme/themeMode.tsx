import {createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode} from 'react'
import type {ThemeDensity, ThemeMode} from './designTokens'

const MODE_STORAGE_KEY = 'cm.theme.mode'
const DENSITY_STORAGE_KEY = 'cm.theme.density'

type ThemeModeContextValue = {
    mode: ThemeMode
    density: ThemeDensity
    setMode: (mode: ThemeMode) => void
    toggleMode: () => void
    setDensity: (density: ThemeDensity) => void
    toggleDensity: () => void
}

const ThemeModeContext = createContext<ThemeModeContextValue | null>(null)

function readStored<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
    if (typeof window === 'undefined') {
        return fallback
    }
    try {
        const stored = window.localStorage.getItem(key)
        return allowed.includes(stored as T) ? (stored as T) : fallback
    } catch {
        // Private browsing / storage disabled — fall back to the default.
        return fallback
    }
}

function persist(key: string, value: string) {
    if (typeof window === 'undefined') {
        return
    }
    try {
        window.localStorage.setItem(key, value)
    } catch {
        // Ignore quota or permission failures; the preference is non-critical.
    }
}

function prefersDark() {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
        return false
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches
}

export function resolveInitialMode(): ThemeMode {
    const stored = readStored<ThemeMode | 'system'>(MODE_STORAGE_KEY, ['light', 'dark', 'system'], 'system')
    if (stored === 'light' || stored === 'dark') {
        return stored
    }
    return prefersDark() ? 'dark' : 'light'
}

export function ThemeModeProvider({children}: { children: ReactNode }) {
    const [mode, setModeState] = useState<ThemeMode>(resolveInitialMode)
    const [density, setDensityState] = useState<ThemeDensity>(
        () => readStored<ThemeDensity>(DENSITY_STORAGE_KEY, ['comfortable', 'compact'], 'comfortable'),
    )

    // The CSS layer keys off these attributes so hand-written styles follow the
    // Ant Design theme without a second source of truth.
    useEffect(() => {
        if (typeof document === 'undefined') {
            return
        }
        document.documentElement.dataset.theme = mode
        document.documentElement.style.colorScheme = mode
    }, [mode])

    useEffect(() => {
        if (typeof document === 'undefined') {
            return
        }
        document.documentElement.dataset.density = density
    }, [density])

    const setMode = useCallback((next: ThemeMode) => {
        setModeState(next)
        persist(MODE_STORAGE_KEY, next)
    }, [])

    const setDensity = useCallback((next: ThemeDensity) => {
        setDensityState(next)
        persist(DENSITY_STORAGE_KEY, next)
    }, [])

    const value = useMemo<ThemeModeContextValue>(() => ({
        mode,
        density,
        setMode,
        setDensity,
        toggleMode: () => setMode(mode === 'dark' ? 'light' : 'dark'),
        toggleDensity: () => setDensity(density === 'compact' ? 'comfortable' : 'compact'),
    }), [density, mode, setDensity, setMode])

    return <ThemeModeContext.Provider value={value}>{children}</ThemeModeContext.Provider>
}

/**
 * Falls back to a static light/comfortable value when no provider is mounted so
 * that isolated component tests do not need to wrap in the whole app shell.
 */
export function useThemeMode(): ThemeModeContextValue {
    const context = useContext(ThemeModeContext)
    if (context) {
        return context
    }
    return {
        mode: 'light',
        density: 'comfortable',
        setMode: () => undefined,
        setDensity: () => undefined,
        toggleMode: () => undefined,
        toggleDensity: () => undefined,
    }
}
