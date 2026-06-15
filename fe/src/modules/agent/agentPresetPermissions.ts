import type {AgentPresetResponse} from './agentTypes'

export function canEditAgentPreset(preset: AgentPresetResponse | undefined, currentUserId?: number) {
    return !preset || (currentUserId !== undefined && preset.createdBy === currentUserId)
}
