import type {NutritionMealPlanResponse, NutritionMealPlanStatus} from './nutritionTypes'

export function selectNearestNutritionMealPlan(
    plans: NutritionMealPlanResponse[],
    statuses: readonly NutritionMealPlanStatus[],
    referenceDate = currentLocalDate(),
) {
    const allowedStatuses = new Set(statuses)
    return plans
        .filter((plan) => allowedStatuses.has(plan.status))
        .sort((left, right) => comparePlanDistance(left, right, referenceDate))[0]
}

function comparePlanDistance(
    left: NutritionMealPlanResponse,
    right: NutritionMealPlanResponse,
    referenceDate: string,
) {
    const leftOffset = dateOffset(left.planDate, referenceDate)
    const rightOffset = dateOffset(right.planDate, referenceDate)
    const distanceDifference = Math.abs(leftOffset) - Math.abs(rightOffset)
    if (distanceDifference !== 0) return distanceDifference

    const leftIsFuture = leftOffset >= 0
    const rightIsFuture = rightOffset >= 0
    if (leftIsFuture !== rightIsFuture) return leftIsFuture ? -1 : 1

    return right.id - left.id
}

function dateOffset(date: string, referenceDate: string) {
    return Date.parse(`${date}T00:00:00Z`) - Date.parse(`${referenceDate}T00:00:00Z`)
}

function currentLocalDate() {
    return new Date().toLocaleDateString('en-CA')
}
