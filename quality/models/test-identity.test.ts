import {expect, test} from 'bun:test'
import {createTestIdentity, TEST_PASSWORD} from './test-identity'

test('creates bounded unique Auto-only registration values', () => {
    const identity = createTestIdentity('quality-run-1', 'duplicate registration')

    expect(identity.accountNo).toMatch(/^auto_/)
    expect(identity.username).toMatch(/^auto_user_/)
    expect(identity.accountNo.length).toBeLessThanOrEqual(64)
    expect(identity.username.length).toBeLessThanOrEqual(64)
    expect(identity.email).toEndWith('@example.test')
    expect(identity.password).toBe(TEST_PASSWORD)
    expect(identity.confirmPassword).toBe(TEST_PASSWORD)
})
