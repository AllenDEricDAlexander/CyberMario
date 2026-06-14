import {requestJson} from '../../services/request'
import type {UserResponse} from '../rbac/rbacTypes'
import type {ChangeCurrentUserPasswordRequest, UpdateCurrentUserProfileRequest} from './accountTypes'

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
