import {describe, expect, test} from 'bun:test'
import {cleanupAutoState, prepareAutoState, type AutoStateAdapter} from './autoState'

describe('Auto state lifecycle', () => {
    test('prepares PostgreSQL before Redis', async () => {
        const calls: string[] = []
        const adapter: AutoStateAdapter = {
            resetPostgres: async () => {
                calls.push('postgres:reset')
            },
            dropPostgres: async () => {
                calls.push('postgres:drop')
            },
            flushRedis: async () => {
                calls.push('redis:flush')
            },
        }

        await prepareAutoState(adapter)

        expect(calls).toEqual(['postgres:reset', 'redis:flush'])
    })

    test('attempts Redis cleanup when PostgreSQL cleanup fails', async () => {
        const calls: string[] = []
        const adapter: AutoStateAdapter = {
            resetPostgres: async () => undefined,
            dropPostgres: async () => {
                calls.push('postgres:drop')
                throw new Error('postgres cleanup failed')
            },
            flushRedis: async () => {
                calls.push('redis:flush')
            },
        }

        await expect(cleanupAutoState(adapter)).rejects.toThrow('Auto state cleanup failed')
        expect(calls).toEqual(['postgres:drop', 'redis:flush'])
    })
})
