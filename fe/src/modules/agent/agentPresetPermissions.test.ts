import {describe, expect, test} from 'vitest'
import {canEditAgentPreset} from './agentPresetPermissions'

describe('agentPresetPermissions', () => {
    test('allows new presets and presets created by current user', () => {
        expect(canEditAgentPreset(undefined, 8)).toBe(true)
        expect(canEditAgentPreset({id: 1, name: 'Mine', enabled: true, createdBy: 8}, 8)).toBe(true)
    })

    test('rejects presets created by another user', () => {
        expect(canEditAgentPreset({id: 1, name: 'Other', enabled: true, createdBy: 9}, 8)).toBe(false)
    })
})
