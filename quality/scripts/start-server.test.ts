import {expect, test} from 'bun:test'
import path from 'node:path'
import {serverDefinition, serverEnvironment} from './start-server'

test('builds dedicated backend and frontend commands', () => {
    const root = path.resolve('/workspace/CyberMario/quality')
    const backend = serverDefinition('backend', root, {
        ...process.env,
        AUTO_FRONTEND_PORT: '5174',
    })
    const frontend = serverDefinition('frontend', root, {
        ...process.env,
        AUTO_FRONTEND_PORT: '5174',
    })

    expect(backend.command).toEqual([
        './mvnw',
        '-Dmaven.build.cache.enabled=false',
        'spring-boot:run',
    ])
    expect(backend.cwd).toBe(path.resolve(root, '../be'))
    expect(frontend.command).toContain('5174')
    expect(frontend.cwd).toBe(path.resolve(root, '../fe'))

    const frontendEnvironment = serverEnvironment('frontend', {
        PATH: '/bin',
        AUTO_FRONTEND_PORT: '5174',
        AUTO_DB_PASSWORD: 'must-not-reach-vite',
        VITE_BACKEND_TARGET: 'http://127.0.0.1:28081',
    })
    expect(frontendEnvironment.AUTO_DB_PASSWORD).toBeUndefined()
    expect(frontendEnvironment.VITE_BACKEND_TARGET).toBe('http://127.0.0.1:28081')
})
