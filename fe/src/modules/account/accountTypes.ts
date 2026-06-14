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
