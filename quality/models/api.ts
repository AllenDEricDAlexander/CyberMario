import type {Response} from '@playwright/test'

export type ApiEnvelope<T = unknown> = {
    code: string
    message: string
    data: T
    traceId?: string
    timestamp?: string
}

export async function readApiEnvelope<T = unknown>(response: Response) {
    return await response.json() as ApiEnvelope<T>
}
