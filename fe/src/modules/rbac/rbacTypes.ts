import type {CodedEnum} from '../../utils/enum'

export type RbacStatus = 'ENABLED' | 'DISABLED' | CodedEnum
export type PermissionStatus = 'ENABLED' | 'DISABLED' | 'DRAFT' | CodedEnum
export type PermissionType = 'MENU' | 'BUTTON' | 'API' | CodedEnum
export type ApiMatcherType = 'EXACT' | 'MVC' | 'ANT' | 'REGEX' | CodedEnum
export type ApiRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | CodedEnum

export type UserResponse = {
    id: number
    accountNo: string
    username: string
    nickname?: string
    email?: string
    mobile?: string
    avatarUrl?: string
    status: RbacStatus
    locked: boolean
    passwordExpired: boolean
    lastLoginAt?: string
    remark?: string
}

export type RoleResponse = {
    id: number
    roleCode: string
    roleName: string
    status: RbacStatus
    sortNo: number
    builtIn: boolean
    description?: string
}

export type PermissionResponse = {
    id: number
    permCode: string
    permName: string
    permType: PermissionType
    parentId?: number
    status: PermissionStatus
    sortNo: number
    description?: string
    menu?: MenuDetail
    button?: ButtonDetail
    api?: ApiDetail
}

export type MenuDetail = {
    parentMenuId?: number
    routeName?: string
    routePath?: string
    component?: string
    redirect?: string
    icon?: string
    hidden: boolean
    cacheable: boolean
    externalLink?: string
}

export type ButtonDetail = {
    menuPermissionId?: number
    buttonKey?: string
    frontendAction?: string
    styleHint?: string
    description?: string
    apiPermissionIds?: number[]
}

export type ApiDetail = {
    httpMethod?: string
    urlPattern?: string
    matcherType?: ApiMatcherType
    publicFlag: boolean
    serviceTag?: string
    operationName?: string
    riskLevel?: ApiRiskLevel
}

export type MenuTreeResponse = {
    permissionId: number
    permCode: string
    permName: string
    routeName?: string
    routePath?: string
    component?: string
    redirect?: string
    icon?: string
    hidden: boolean
    cacheable: boolean
    externalLink?: string
    sortNo: number
    children: MenuTreeResponse[]
}

export type EffectivePermissionResponse = {
    roleIds: number[]
    roleCodes: string[]
    menuCodes: string[]
    buttonCodes: string[]
    apiCodes: string[]
}

export type PermissionRequest = {
    permCode: string
    permName: string
    permType: 'MENU' | 'BUTTON' | 'API'
    parentId?: number | null
    status?: 'ENABLED' | 'DISABLED' | 'DRAFT'
    sortNo?: number
    description?: string
    menu?: Partial<MenuDetail>
    button?: Partial<ButtonDetail>
    api?: Partial<ApiDetail>
}

export type CreateUserRequest = {
    accountNo: string
    username: string
    nickname?: string
    email?: string
    mobile?: string
    avatarUrl?: string
    initialPassword: string
    status?: 'ENABLED' | 'DISABLED'
    remark?: string
    roleIds?: number[]
}

export type UpdateUserRequest = Omit<Partial<CreateUserRequest>, 'initialPassword' | 'roleIds'> & {
    locked?: boolean
    passwordExpired?: boolean
}

export type CreateRoleRequest = {
    roleCode: string
    roleName: string
    status?: 'ENABLED' | 'DISABLED'
    sortNo?: number
    builtIn?: boolean
    description?: string
}

export type UpdateRoleRequest = Partial<Omit<CreateRoleRequest, 'roleCode'>>

export type ApiPermissionRule = {
    permissionId?: number
    permCode?: string
    httpMethod?: string
    urlPattern?: string
    matcherType?: ApiMatcherType
    publicFlag?: boolean
    serviceTag?: string
    operationName?: string
    riskLevel?: ApiRiskLevel
}
