import {describe, expect, test} from 'bun:test'
import {loadAutoEnvironment, safeEnvironmentSummary} from './autoEnvironment'

const safeSource = {
    AUTO_CLEANUP_ALLOWED: 'true',
    AUTO_DB_URL: 'jdbc:postgresql://localhost:5432/cyber_mario_auto',
    AUTO_DB_USERNAME: 'postgres',
    AUTO_DB_PASSWORD: 'database-secret',
    AUTO_DB_SCHEMA: 'auto_local',
    AUTO_REDIS_HOST: 'localhost',
    AUTO_REDIS_PORT: '6379',
    AUTO_REDIS_PASSWORD: 'redis-secret',
    AUTO_REDIS_DATABASE: '15',
    AUTO_JWT_SECRET: 'auto-jwt-secret-that-is-at-least-32-bytes',
    AUTO_ADMIN_PASSWORD: 'AutoAdmin#2026',
    AUTO_BACKEND_PORT: '28081',
    AUTO_FRONTEND_PORT: '5174',
}

describe('loadAutoEnvironment', () => {
    test('accepts an explicit Auto lane', () => {
        const environment = loadAutoEnvironment(safeSource)

        expect(environment.postgres.database).toBe('cyber_mario_auto')
        expect(environment.postgres.schema).toBe('auto_local')
        expect(environment.redis.database).toBe(15)
        expect(environment.backendPort).toBe(28081)
        expect(environment.frontendPort).toBe(5174)
        expect(environment.runId).toMatch(/^quality-[a-z0-9-]+$/)
    })

    test('rejects disabled destructive cleanup', () => {
        expect(() => loadAutoEnvironment({...safeSource, AUTO_CLEANUP_ALLOWED: 'false'}))
            .toThrow('AUTO_CLEANUP_ALLOWED=true')
    })

    test('rejects a non-Auto database', () => {
        expect(() => loadAutoEnvironment({
            ...safeSource,
            AUTO_DB_URL: 'jdbc:postgresql://localhost:5432/cyber_mario',
        })).toThrow('Auto database')
    })

    test('rejects public schema and the development Redis lane', () => {
        expect(() => loadAutoEnvironment({...safeSource, AUTO_DB_SCHEMA: 'public'}))
            .toThrow('auto_* schema')
        expect(() => loadAutoEnvironment({...safeSource, AUTO_REDIS_DATABASE: '1'}))
            .toThrow('Auto Redis database')
    })

    test('redacts every secret from diagnostics', () => {
        const summary = JSON.stringify(safeEnvironmentSummary(loadAutoEnvironment(safeSource)))

        expect(summary).not.toContain('database-secret')
        expect(summary).not.toContain('redis-secret')
        expect(summary).not.toContain('auto-jwt-secret')
        expect(summary).not.toContain('AutoAdmin#2026')
    })
})
