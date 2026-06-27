import type {MenuTreeResponse, UserResponse} from '../rbac/rbacTypes'

export type LoginRequest = {
    account: string
    password: string
}

export type RegisterRequest = {
    accountNo: string
    username: string
    password: string
    nickname?: string
    email?: string
    mobile?: string
    avatarUrl?: string
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
    permissionVersion?: string | null
}
