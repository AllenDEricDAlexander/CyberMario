type SearchValue = string | number | boolean | null | undefined

export function buildSearchParams(params: Record<string, SearchValue>) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
        if (value === null || value === undefined || value === '') {
            return
        }
        search.set(key, String(value))
    })
    return search.toString()
}
