import {describe, expect, test} from 'vitest'
import type {UserResponse} from '../rbacTypes'
import {activationActionsFor, activationStatusLabel} from './UserListPage'

const baseUser: UserResponse = {
    id: 7,
    accountNo: 'mario',
    username: 'mario',
    status: 'ENABLED',
    locked: false,
    passwordExpired: true,
    activationStatus: 'PENDING_ACTIVATION',
}

describe('UserListPage activation actions', () => {
    test('pending users can reissue but cannot reset when both permissions exist', () => {
        expect(activationActionsFor(baseUser, {resetPassword: true, resendActivation: true}))
            .toEqual({resetPassword: false, resendActivation: true})
        expect(activationStatusLabel(baseUser)).toBe('待激活')
    })

    test('activated users can reset but cannot reissue', () => {
        const activated = {...baseUser, activationStatus: 'ACTIVATED' as const, passwordExpired: false}
        expect(activationActionsFor(activated, {resetPassword: true, resendActivation: true}))
            .toEqual({resetPassword: true, resendActivation: false})
        expect(activationStatusLabel(activated)).toBe('已激活')
    })

    test('resend remains hidden without its button permission', () => {
        expect(activationActionsFor(baseUser, {resetPassword: true, resendActivation: false}))
            .toEqual({resetPassword: false, resendActivation: false})
    })
})
