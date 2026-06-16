export const PERMISSION_VERSION_HEADER = 'X-Rbac-Permission-Version'

type PermissionVersionListener = (permissionVersion: string) => void

const listeners = new Set<PermissionVersionListener>()

export function subscribePermissionVersion(listener: PermissionVersionListener) {
    listeners.add(listener)
    return () => {
        listeners.delete(listener)
    }
}

export function publishResponsePermissionVersion(response: Response) {
    publishPermissionVersion(response.headers.get(PERMISSION_VERSION_HEADER))
}

export function publishPermissionVersion(permissionVersion: string | null | undefined) {
    if (!permissionVersion) {
        return
    }
    listeners.forEach((listener) => listener(permissionVersion))
}
