import {describe, expect, test, vi} from 'vitest'
import {voidify} from './async'

describe('voidify', () => {
    test('forwards arguments to promise handlers without returning the promise', () => {
        const handler = vi.fn((id: number) => {
            expect(id).toBe(42)
            return Promise.resolve()
        })
        const wrapped = voidify(handler)

        const result = wrapped(42)

        expect(result).toBeUndefined()
        expect(handler).toHaveBeenCalledWith(42)
    })
})
