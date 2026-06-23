import {requestJson} from '../../services/request'
import type {UserResponse} from '../rbac/rbacTypes'
import type {
    AgentSoulMdResponse,
    AgentSoulMdUpdateRequest,
    AgentSoulMdVersionResponse,
    ChangeCurrentUserPasswordRequest,
    UpdateCurrentUserProfileRequest,
} from './accountTypes'

export function updateCurrentUserProfile(request: UpdateCurrentUserProfileRequest) {
    return requestJson<UserResponse>('/api/me/profile', {
        method: 'PUT',
        body: request,
    })
}

export function changeCurrentUserPassword(request: ChangeCurrentUserPasswordRequest) {
    return requestJson<void>('/api/me/password', {
        method: 'PUT',
        body: request,
    })
}

export function getCurrentUserSoulMd() {
    return requestJson<AgentSoulMdResponse>('/api/me/soul-md')
}

export function updateCurrentUserSoulMd(request: AgentSoulMdUpdateRequest) {
    return requestJson<AgentSoulMdResponse>('/api/me/soul-md', {
        method: 'PUT',
        body: request,
    })
}

export function getCurrentUserSoulMdVersions() {
    return requestJson<AgentSoulMdVersionResponse[]>('/api/me/soul-md/versions')
}
