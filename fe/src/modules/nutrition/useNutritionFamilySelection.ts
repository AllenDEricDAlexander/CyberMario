import {useCallback, useEffect, useMemo, useState} from 'react'
import {ApiRequestError} from '../../types/api'
import {
    getCurrentNutritionFamilyId,
    setCurrentNutritionFamilyId,
    useCurrentNutritionFamilyId,
} from './currentFamilyStore'
import {listNutritionFamilies} from './nutritionService'
import type {NutritionFamilyResponse, NutritionLoadState} from './nutritionTypes'

export function useNutritionFamilySelection() {
    const currentFamilyId = useCurrentNutritionFamilyId()
    const [families, setFamilies] = useState<NutritionFamilyResponse[]>([])
    const [state, setState] = useState<NutritionLoadState>('idle')
    const [error, setError] = useState<string>()

    const reload = useCallback(async () => {
        setState('loading')
        setError(undefined)
        try {
            const accessibleFamilies = await listNutritionFamilies()
            setFamilies(accessibleFamilies)
            if (accessibleFamilies.length === 0) {
                setCurrentNutritionFamilyId(null)
                setState('empty')
                return
            }
            const storedFamilyId = getCurrentNutritionFamilyId()
            const selectedId = storedFamilyId != null
                && accessibleFamilies.some((family) => family.id === storedFamilyId)
                ? storedFamilyId
                : accessibleFamilies[0].id
            setCurrentNutritionFamilyId(selectedId)
            setState('ready')
        } catch (reason) {
            setFamilies([])
            setCurrentNutritionFamilyId(null)
            setError(errorMessage(reason))
            setState(isForbidden(reason) ? 'forbidden' : 'error')
        }
    }, [])

    useEffect(() => {
        void reload()
    }, [reload])

    const currentFamily = useMemo(
        () => families.find((family) => family.id === currentFamilyId) ?? null,
        [currentFamilyId, families],
    )

    return {
        families,
        currentFamily,
        currentFamilyId,
        setCurrentFamilyId: setCurrentNutritionFamilyId,
        state,
        error,
        reload,
    }
}

function isForbidden(reason: unknown) {
    return reason instanceof ApiRequestError
        ? reason.status === 403
        : typeof reason === 'object' && reason !== null && 'status' in reason && reason.status === 403
}

function errorMessage(reason: unknown) {
    return reason instanceof Error
        ? reason.message
        : typeof reason === 'object' && reason !== null && 'message' in reason && typeof reason.message === 'string'
            ? reason.message
            : '加载营养家庭失败'
}
