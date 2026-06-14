import {describe, expect, test} from 'vitest'
import {ragButtonCodes} from '../../modules/rag/ragPermissionCodes'
import {rbacButtonCodes} from '../../modules/rbac/rbacPermissionCodes'
import {isCurrentPathAffectedByLostButtons} from './permissionImpact'

describe('isCurrentPathAffectedByLostButtons', () => {
    test('matches lost button permissions by current route family', () => {
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [rbacButtonCodes.user.edit])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/rag/documents/12', [ragButtonCodes.chunk.toggle])).toBe(true)
    })

    test('ignores unrelated routes and empty permission changes', () => {
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [rbacButtonCodes.role.edit])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/chat', [rbacButtonCodes.user.edit])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [])).toBe(false)
    })
})
