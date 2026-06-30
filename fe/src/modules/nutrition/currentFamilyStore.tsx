import {useSyncExternalStore} from 'react'

type Listener = () => void

let currentFamilyId: number | null = null
const listeners = new Set<Listener>()

export function getCurrentNutritionFamilyId() {
    return currentFamilyId
}

export function setCurrentNutritionFamilyId(familyId: number | null) {
    currentFamilyId = familyId
    emitCurrentFamilyChange()
}

export function clearCurrentNutritionFamilyId() {
    currentFamilyId = null
    emitCurrentFamilyChange()
}

export function subscribeCurrentNutritionFamily(listener: Listener) {
    listeners.add(listener)
    return () => {
        listeners.delete(listener)
    }
}

export function useCurrentNutritionFamilyId() {
    return useSyncExternalStore(
        subscribeCurrentNutritionFamily,
        getCurrentNutritionFamilyId,
        getCurrentNutritionFamilyId,
    )
}

function emitCurrentFamilyChange() {
    listeners.forEach((listener) => listener())
}
