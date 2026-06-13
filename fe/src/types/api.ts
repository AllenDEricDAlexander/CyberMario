export type ApiResponse<T> = {
    code: string
    message: string
    data: T
    traceId?: string
    timestamp?: string
}

export type PageResult<T> = {
    records: T[]
    page: number
    size: number
    total: number
    totalPages: number
}

export class ApiRequestError extends Error {
    code: string
    status?: number
    traceId?: string

    constructor(message: string, options: { code: string; status?: number; traceId?: string }) {
        super(message)
        this.name = 'ApiRequestError'
        this.code = options.code
        this.status = options.status
        this.traceId = options.traceId
    }
}
