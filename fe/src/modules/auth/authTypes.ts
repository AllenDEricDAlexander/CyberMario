import type {MenuTreeResponse, UserResponse} from '../rbac/rbacTypes'

export type LoginRequest = {
    username: string
    password: string
}

export type LoginResponse = {
    accessToken?: string | null
    refreshToken?: string | null
    accessTokenExpiresInSeconds: number
    refreshTokenExpiresInSeconds: number
    user: UserResponse
    roleCodes: string[]
    menus: MenuTreeResponse[]
    buttonCodes: string[]
    permissionCodes: string[]
}
