export type UpdateCurrentUserProfileRequest = {
    nickname?: string
    email?: string
    mobile?: string
    avatarUrl?: string
}

export type ChangeCurrentUserPasswordRequest = {
    currentPassword: string
    newPassword: string
    confirmPassword: string
}

export type AgentSoulMdResponse = {
    contentMarkdown: string
    enabled: boolean
    contentChars: number
    maxChars: number
    versionNo: number
    updatedAt?: string
}

export type AgentSoulMdUpdateRequest = {
    contentMarkdown: string
    enabled: boolean
}

export type AgentSoulChangeType = 'MANUAL_EDIT' | 'AGENT_CHAT_AUTO_UPDATE'
export type AgentSoulSourceType = 'AGENT_CHAT' | 'EXTERNAL_API'

export type AgentSoulMdVersionResponse = {
    id: number
    versionNo: number
    contentMarkdown: string
    contentChars: number
    changeType?: AgentSoulChangeType
    changeSummary?: string
    sourceType?: AgentSoulSourceType
    sourceSessionId?: string
    sourceMessageIds?: string
    modelProvider?: string
    modelName?: string
    requestId?: string
    traceId?: string
    createdAt?: string
}
