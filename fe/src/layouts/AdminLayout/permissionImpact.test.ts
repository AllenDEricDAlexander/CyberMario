import {describe, expect, test} from 'vitest'
import {mcpButtonCodes} from '../../modules/agent/mcp/mcpPermissionCodes'
import {ragButtonCodes} from '../../modules/rag/ragPermissionCodes'
import {rbacButtonCodes} from '../../modules/rbac/rbacPermissionCodes'
import {isCurrentPathAffectedByLostButtons} from './permissionImpact'

describe('isCurrentPathAffectedByLostButtons', () => {
    test('matches lost button permissions by current route family', () => {
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [rbacButtonCodes.user.edit])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/rag/documents/12', [ragButtonCodes.chunk.toggle])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/servers', [mcpButtonCodes.server.toggle])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/servers', [mcpButtonCodes.tool.editPolicy])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/servers', [mcpButtonCodes.tool.toggle])).toBe(true)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/logs', [mcpButtonCodes.log.view])).toBe(true)
    })

    test('ignores unrelated routes and empty permission changes', () => {
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [rbacButtonCodes.role.edit])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/tools', [mcpButtonCodes.tool.editPolicy])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/agent/mcp/logs', [mcpButtonCodes.tool.toggle])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/chat', [rbacButtonCodes.user.edit])).toBe(false)
        expect(isCurrentPathAffectedByLostButtons('/rbac/users', [])).toBe(false)
    })
})
