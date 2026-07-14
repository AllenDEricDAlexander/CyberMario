import {randomUUID} from 'node:crypto'

export const TEST_PASSWORD = 'AutoGate#2026!'

export type TestIdentity = {
    accountNo: string
    username: string
    nickname: string
    email: string
    password: string
    confirmPassword: string
}

export function createTestIdentity(runId: string, caseId: string): TestIdentity {
    const suffix = sanitize(`${runId}_${caseId}_${randomUUID().slice(0, 8)}`)
    return {
        accountNo: `auto_${suffix}`.slice(0, 64),
        username: `auto_user_${suffix}`.slice(0, 64),
        nickname: `Auto ${sanitize(caseId).slice(0, 32)}`,
        email: `${suffix.slice(0, 80)}@example.test`,
        password: TEST_PASSWORD,
        confirmPassword: TEST_PASSWORD,
    }
}

function sanitize(value: string) {
    return value
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '_')
        .replace(/^_+|_+$/g, '')
}
