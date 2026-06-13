export const RBAC_STATUS_OPTIONS = [
    {value: 'ENABLED', label: '启用'},
    {value: 'DISABLED', label: '禁用'},
]

export const PERMISSION_STATUS_OPTIONS = [
    {value: 'ENABLED', label: '启用'},
    {value: 'DISABLED', label: '禁用'},
    {value: 'DRAFT', label: '草稿'},
]

export const PERMISSION_TYPE_OPTIONS = [
    {value: 'MENU', label: '菜单'},
    {value: 'BUTTON', label: '按钮'},
    {value: 'API', label: '接口'},
]

export const API_MATCHER_TYPE_OPTIONS = [
    {value: 'EXACT', label: '精确匹配'},
    {value: 'MVC', label: 'MVC匹配'},
    {value: 'ANT', label: 'Ant匹配'},
    {value: 'REGEX', label: '正则匹配'},
]

export const API_RISK_LEVEL_OPTIONS = [
    {value: 'LOW', label: '低'},
    {value: 'MEDIUM', label: '中'},
    {value: 'HIGH', label: '高'},
]

export const HTTP_METHOD_OPTIONS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'].map((method) => ({
    value: method,
    label: method,
}))
