import {requestJson} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {PageResult} from '../../types/api'
import type {
    ApiPermissionRule,
    ActivationDeliveryResponse,
    AdminUserCreateResponse,
    CreateRoleRequest,
    CreateUserRequest,
    EffectivePermissionResponse,
    MenuTreeResponse,
    PermissionRequest,
    PermissionResponse,
    RoleResponse,
    UpdateRoleRequest,
    UpdateUserRequest,
    UserResponse,
} from './rbacTypes'

type PageParams = {
    page?: number
    size?: number
}

export function getUsers(params: PageParams) {
    return requestJson<PageResult<UserResponse>>(`/api/admin/users?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
    })}`)
}

export function createUser(request: CreateUserRequest) {
    return requestJson<AdminUserCreateResponse>('/api/admin/users', {
        method: 'POST',
        body: request,
    })
}

export function reissueUserActivation(id: number) {
    return requestJson<ActivationDeliveryResponse>(`/api/admin/users/${id}/activation-token`, {
        method: 'POST',
    })
}

export function updateUser(id: number, request: UpdateUserRequest) {
    return requestJson<UserResponse>(`/api/admin/users/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deleteUser(id: number) {
    return requestJson<void>(`/api/admin/users/${id}`, {
        method: 'DELETE',
    })
}

export function updateUserStatus(id: number, status: 'ENABLED' | 'DISABLED') {
    return requestJson<void>(`/api/admin/users/${id}/status`, {
        method: 'PATCH',
        body: {rbacStatus: status},
    })
}

export function resetUserPassword(id: number, password: string) {
    return requestJson<void>(`/api/admin/users/${id}/password`, {
        method: 'PUT',
        body: {password},
    })
}

export function getUserRoles(id: number) {
    return requestJson<number[]>(`/api/admin/users/${id}/roles`)
}

export function replaceUserRoles(id: number, ids: number[]) {
    return requestJson<void>(`/api/admin/users/${id}/roles`, {
        method: 'PUT',
        body: {ids},
    })
}

export function getUserEffectivePermissions(id: number) {
    return requestJson<EffectivePermissionResponse>(`/api/admin/users/${id}/permissions/effective`)
}

export function getRoles(params: PageParams) {
    return requestJson<PageResult<RoleResponse>>(`/api/admin/roles?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
    })}`)
}

export function createRole(request: CreateRoleRequest) {
    return requestJson<RoleResponse>('/api/admin/roles', {
        method: 'POST',
        body: request,
    })
}

export function updateRole(id: number, request: UpdateRoleRequest) {
    return requestJson<RoleResponse>(`/api/admin/roles/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deleteRole(id: number) {
    return requestJson<void>(`/api/admin/roles/${id}`, {
        method: 'DELETE',
    })
}

export function getRolePermissions(id: number) {
    return requestJson<number[]>(`/api/admin/roles/${id}/permissions`)
}

export function getRoleEffectivePermissions(id: number) {
    return requestJson<number[]>(`/api/admin/roles/${id}/permissions/effective`)
}

export function replaceRolePermissions(id: number, ids: number[], syncButtonApis: boolean) {
    return requestJson<number[]>(`/api/admin/roles/${id}/permissions`, {
        method: 'PUT',
        body: {ids, syncButtonApis},
    })
}

export function getRoleInheritance(id: number) {
    return requestJson<number[]>(`/api/admin/roles/${id}/inheritance`)
}

export function replaceRoleInheritance(id: number, ids: number[]) {
    return requestJson<void>(`/api/admin/roles/${id}/inheritance`, {
        method: 'PUT',
        body: {ids},
    })
}

export function getPermissions(params: PageParams) {
    return requestJson<PageResult<PermissionResponse>>(`/api/admin/permissions?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 50,
    })}`)
}

export function createPermission(request: PermissionRequest) {
    return requestJson<PermissionResponse>('/api/admin/permissions', {
        method: 'POST',
        body: request,
    })
}

export function updatePermission(id: number, request: PermissionRequest) {
    return requestJson<PermissionResponse>(`/api/admin/permissions/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deletePermission(id: number) {
    return requestJson<void>(`/api/admin/permissions/${id}`, {
        method: 'DELETE',
    })
}

export function updatePermissionStatus(id: number, status: 'ENABLED' | 'DISABLED' | 'DRAFT') {
    return requestJson<void>(`/api/admin/permissions/${id}/status`, {
        method: 'PATCH',
        body: {permissionStatus: status},
    })
}

export function getMenuTree() {
    return requestJson<MenuTreeResponse[]>('/api/admin/menus/tree')
}

export function createMenu(request: PermissionRequest) {
    return requestJson<PermissionResponse>('/api/admin/menus', {
        method: 'POST',
        body: request,
    })
}

export function updateMenu(id: number, request: PermissionRequest) {
    return requestJson<PermissionResponse>(`/api/admin/menus/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deleteMenu(id: number) {
    return requestJson<void>(`/api/admin/menus/${id}`, {
        method: 'DELETE',
    })
}

export function getButtons(menuId: number) {
    return requestJson<PermissionResponse[]>(`/api/admin/buttons?menuId=${menuId}`)
}

export function createButton(request: PermissionRequest) {
    return requestJson<PermissionResponse>('/api/admin/buttons', {
        method: 'POST',
        body: request,
    })
}

export function updateButton(id: number, request: PermissionRequest) {
    return requestJson<PermissionResponse>(`/api/admin/buttons/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deleteButton(id: number) {
    return requestJson<void>(`/api/admin/buttons/${id}`, {
        method: 'DELETE',
    })
}

export function getButtonApis(id: number) {
    return requestJson<number[]>(`/api/admin/buttons/${id}/apis`)
}

export function replaceButtonApis(id: number, ids: number[]) {
    return requestJson<void>(`/api/admin/buttons/${id}/apis`, {
        method: 'PUT',
        body: {ids},
    })
}

export function getApiPermissionRules() {
    return requestJson<ApiPermissionRule[]>('/api/admin/api-permissions/rules')
}

export function getApiPermissions(params: PageParams) {
    return requestJson<PageResult<PermissionResponse>>(`/api/admin/api-permissions?${buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
    })}`)
}

export function createApiPermission(request: PermissionRequest) {
    return requestJson<PermissionResponse>('/api/admin/api-permissions', {
        method: 'POST',
        body: request,
    })
}

export function updateApiPermission(id: number, request: PermissionRequest) {
    return requestJson<PermissionResponse>(`/api/admin/api-permissions/${id}`, {
        method: 'PUT',
        body: request,
    })
}

export function deleteApiPermission(id: number) {
    return requestJson<void>(`/api/admin/api-permissions/${id}`, {
        method: 'DELETE',
    })
}
