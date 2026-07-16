import {Button, List, Space, Tag} from 'antd'
import {useState} from 'react'
import type {ImJoinPolicy, ImJoinResultStatus, ImMemberRole, ImMembershipStatus, ImSurfaceType, JoinResultView} from '../imTypes'

type SurfaceIdentity = {
    surfaceType: ImSurfaceType
    surfaceId: number
}

export type PendingReviewRequest = {
    requestId: number
    userLabel: string
}

type JoinAction = 'apply' | 'cancel' | 'leave' | 'none'
type JoinResultAction = JoinAction | 'approve' | 'reject'
type ImJoinApplyUiStatus = ImMembershipStatus | ImJoinResultStatus | 'NONE'
type ScopedJoinResult = {
    result: JoinResultView
    surfaceKey: string
}
type ScopedReviewedRequestIds = {
    requestIds: number[]
    surfaceKey: string
}
const applyCapableStatuses: ImJoinApplyUiStatus[] = ['NONE', 'LEFT', 'REJECTED', 'CANCELLED']

export type ImJoinApplyControlsProps = {
    surface: SurfaceIdentity
    joinPolicy: ImJoinPolicy
    currentMembershipStatus?: ImMembershipStatus | 'NONE' | null
    currentMemberRole?: ImMemberRole | null
    pendingRequestId?: number | null
    pendingReviewRequests?: PendingReviewRequest[]
    onApply: (surface: SurfaceIdentity) => Promise<JoinResultView> | JoinResultView
    onCancel: (requestId: number) => Promise<JoinResultView> | JoinResultView
    onApprove: (requestId: number) => Promise<JoinResultView> | JoinResultView
    onReject: (requestId: number) => Promise<JoinResultView> | JoinResultView
    onLeave: (surface: SurfaceIdentity) => Promise<void> | void
}

export type ImJoinApplyState = {
    status: ImJoinApplyUiStatus
    role?: ImMemberRole | null
    policy: ImJoinPolicy
    primaryAction: JoinAction
    primaryText: string
    statusText: string
    reviewActions: PendingReviewRequest[]
}

export function deriveImJoinApplyState(input: {
    joinPolicy: ImJoinPolicy
    membershipStatus?: ImJoinApplyUiStatus | null
    memberRole?: ImMemberRole | null
    pendingRequestId?: number | null
    pendingReviewRequests?: PendingReviewRequest[]
}): ImJoinApplyState {
    const status = input.membershipStatus ?? 'NONE'
    const reviewer = input.memberRole === 'OWNER' || input.memberRole === 'ADMIN'
    const reviewActions = reviewer ? input.pendingReviewRequests ?? [] : []

    if (status === 'PENDING' && input.pendingRequestId) {
        return state(input, status, 'cancel', 'Cancel request', 'Request pending', reviewActions)
    }
    if (status === 'ACTIVE' && input.memberRole !== 'OWNER') {
        return state(input, status, 'leave', 'Leave', 'Joined', reviewActions)
    }
    if (applyCapableStatuses.includes(status)) {
        return state(input, status, 'apply', input.joinPolicy === 'OPEN' ? 'Join' : 'Apply to join', policyText(input.joinPolicy), reviewActions)
    }
    return state(input, status, 'none', '', statusText(status), reviewActions)
}

export function resolveSelfJoinResult(action: JoinResultAction, result?: JoinResultView | void) {
    if (!result) {
        return undefined
    }
    return action === 'apply' || action === 'cancel' ? result : undefined
}

export function resolveScopedJoinResult(scopedResult: ScopedJoinResult | undefined, surface: SurfaceIdentity) {
    return scopedResult?.surfaceKey === surfaceIdentityKey(surface) ? scopedResult.result : undefined
}

export function resolveScopedReviewedRequestIds(scopedRequestIds: ScopedReviewedRequestIds | undefined, surface: SurfaceIdentity) {
    return scopedRequestIds?.surfaceKey === surfaceIdentityKey(surface) ? scopedRequestIds.requestIds : []
}

export function ImJoinApplyControls(props: ImJoinApplyControlsProps) {
    const [scopedResult, setScopedResult] = useState<ScopedJoinResult>()
    const [scopedReviewedRequestIds, setScopedReviewedRequestIds] = useState<ScopedReviewedRequestIds>()
    const [inFlight, setInFlight] = useState(false)
    const [error, setError] = useState<string>()
    const currentSurfaceKey = surfaceIdentityKey(props.surface)
    const result = resolveScopedJoinResult(scopedResult, props.surface)
    const reviewedRequestIds = resolveScopedReviewedRequestIds(scopedReviewedRequestIds, props.surface)
    const effectiveStatus = result?.status ?? props.currentMembershipStatus
    const effectivePendingRequestId = result?.joinRequestId ?? props.pendingRequestId
    const pendingReviewRequests = props.pendingReviewRequests?.filter((request) => !reviewedRequestIds.includes(request.requestId))
    const viewState = deriveImJoinApplyState({
        joinPolicy: props.joinPolicy,
        membershipStatus: effectiveStatus,
        memberRole: props.currentMemberRole,
        pendingRequestId: effectivePendingRequestId,
        pendingReviewRequests,
    })

    async function runAction(
        actionType: JoinResultAction,
        action: () => Promise<JoinResultView | void> | JoinResultView | void,
        reviewedRequestId?: number,
    ) {
        setInFlight(true)
        setError(undefined)
        try {
            const nextResult = await action()
            const selfResult = resolveSelfJoinResult(actionType, nextResult)
            if (selfResult) {
                setScopedResult({result: selfResult, surfaceKey: currentSurfaceKey})
            }
            if ((actionType === 'approve' || actionType === 'reject') && reviewedRequestId) {
                setScopedReviewedRequestIds((current) => {
                    const requestIds = current?.surfaceKey === currentSurfaceKey ? current.requestIds : []
                    return {
                        requestIds: requestIds.includes(reviewedRequestId) ? requestIds : [...requestIds, reviewedRequestId],
                        surfaceKey: currentSurfaceKey,
                    }
                })
            }
        } catch (nextError) {
            setError(nextError instanceof Error ? nextError.message : 'Action failed')
        } finally {
            setInFlight(false)
        }
    }

    return (
        <Space orientation="vertical" size={8}>
            <Space size={8} wrap>
                <Tag>{policyText(props.joinPolicy)}</Tag>
                <Tag>{viewState.statusText}</Tag>
                {props.currentMemberRole && <Tag>{props.currentMemberRole}</Tag>}
                {renderPrimaryAction(
                    viewState,
                    inFlight,
                    () => {
                        void runAction(viewState.primaryAction, () => runPrimaryAction(props, viewState, effectivePendingRequestId))
                    },
                )}
            </Space>
            {error && <span role="alert">{error}</span>}
            {viewState.reviewActions.length > 0 && (
                <List
                    dataSource={viewState.reviewActions}
                    renderItem={(request) => (
                        <List.Item
                            actions={[
                                <Button
                                    disabled={inFlight}
                                    key="approve"
                                    onClick={() => {
                                        void runAction('approve', () => props.onApprove(request.requestId), request.requestId)
                                    }}
                                    size="small"
                                    type="primary"
                                >
                                    Approve
                                </Button>,
                                <Button
                                    danger
                                    disabled={inFlight}
                                    key="reject"
                                    onClick={() => {
                                        void runAction('reject', () => props.onReject(request.requestId), request.requestId)
                                    }}
                                    size="small"
                                >
                                    Reject
                                </Button>,
                            ]}
                        >
                            {request.userLabel}
                        </List.Item>
                    )}
                    rowKey="requestId"
                    size="small"
                />
            )}
        </Space>
    )
}

function state(
    input: {
        joinPolicy: ImJoinPolicy
        memberRole?: ImMemberRole | null
    },
    status: ImJoinApplyUiStatus,
    primaryAction: JoinAction,
    primaryText: string,
    nextStatusText: string,
    reviewActions: PendingReviewRequest[],
): ImJoinApplyState {
    return {
        status,
        role: input.memberRole,
        policy: input.joinPolicy,
        primaryAction,
        primaryText,
        statusText: nextStatusText,
        reviewActions,
    }
}

function renderPrimaryAction(viewState: ImJoinApplyState, inFlight: boolean, onClick: () => void) {
    if (viewState.primaryAction === 'none') {
        return null
    }
    return (
        <Button
            danger={viewState.primaryAction === 'leave'}
            disabled={inFlight}
            loading={inFlight}
            onClick={onClick}
            type={viewState.primaryAction === 'apply' ? 'primary' : 'default'}
        >
            {viewState.primaryText}
        </Button>
    )
}

function runPrimaryAction(
    props: ImJoinApplyControlsProps,
    viewState: ImJoinApplyState,
    pendingRequestId?: number | null,
) {
    if (viewState.primaryAction === 'apply') {
        return props.onApply(props.surface)
    }
    if (viewState.primaryAction === 'cancel' && pendingRequestId) {
        return props.onCancel(pendingRequestId)
    }
    if (viewState.primaryAction === 'leave') {
        return props.onLeave(props.surface)
    }
    return undefined
}

function surfaceIdentityKey(surface: SurfaceIdentity) {
    return `${surface.surfaceType}:${surface.surfaceId}`
}

function policyText(joinPolicy: ImJoinPolicy) {
    switch (joinPolicy) {
        case 'OPEN':
            return 'Open'
        case 'APPROVAL':
            return 'Approval'
        default:
            return joinPolicy
    }
}

function statusText(status: ImJoinApplyUiStatus) {
    switch (status) {
        case 'NONE':
            return 'Not joined'
        case 'PENDING':
            return 'Request pending'
        case 'ACTIVE':
            return 'Joined'
        case 'LEFT':
            return 'Left'
        case 'BANNED':
            return 'Banned'
        case 'REJECTED':
            return 'Rejected'
        case 'CANCELLED':
            return 'Cancelled'
        default:
            return status
    }
}
