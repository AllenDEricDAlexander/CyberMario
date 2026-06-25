import {describe, expect, test, vi} from 'vitest'
import {registerAsyncErrorHandler, registerUnhandledRejectionReporter, voidify} from './async'

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

    test('reports rejected promise handlers through the registered global handler', async () => {
        const requestError = new Error('无权访问')
        const errorHandler = vi.fn()
        const dispose = registerAsyncErrorHandler(errorHandler)
        const wrapped = voidify(() => Promise.reject(requestError))

        wrapped()
        await Promise.resolve()

        expect(errorHandler).toHaveBeenCalledWith(requestError)
        dispose()
    })

    test('reports current-user profile save failures through the same global handler', async () => {
        const forbiddenError = new Error('没有权限执行该操作')
        const errorHandler = vi.fn()
        const dispose = registerAsyncErrorHandler(errorHandler)
        const saveProfile = voidify(() => Promise.reject(forbiddenError))

        saveProfile()
        await Promise.resolve()

        expect(errorHandler).toHaveBeenCalledWith(forbiddenError)
        dispose()
    })

    test('reports unhandled request rejections through the registered reporter', () => {
        const forbiddenError = new Error('没有权限执行该操作')
        const errorHandler = vi.fn()
        let listener: ((event: PromiseRejectionEvent) => void) | undefined
        const preventDefault = vi.fn()
        const target = {
            addEventListener: vi.fn((_: 'unhandledrejection', nextListener: (event: PromiseRejectionEvent) => void) => {
                listener = nextListener
            }),
            removeEventListener: vi.fn(),
        }
        const dispose = registerUnhandledRejectionReporter(errorHandler, target)
        const event = {
            reason: forbiddenError,
            preventDefault,
        } as unknown as PromiseRejectionEvent

        listener?.(event)

        expect(errorHandler).toHaveBeenCalledWith(forbiddenError)
        expect(preventDefault).toHaveBeenCalled()
        dispose()
        expect(target.removeEventListener).toHaveBeenCalledWith('unhandledrejection', listener)
    })
})
