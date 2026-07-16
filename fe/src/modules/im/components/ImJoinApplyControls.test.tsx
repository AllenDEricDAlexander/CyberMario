import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    deriveImJoinApplyState,
    ImJoinApplyControls,
    resolveScopedJoinResult,
    resolveScopedReviewedRequestIds,
    resolveSelfJoinResult,
} from './ImJoinApplyControls'
import type {JoinResultView} from '../imTypes'

const activeResult: JoinResultView = {
    status: 'ACTIVE',
    surfaceType: 'CHANNEL',
    surfaceId: 1,
    membershipId: 10,
    joinRequestId: null,
}

const pendingResult: JoinResultView = {
    status: 'PENDING',
    surfaceType: 'GROUP',
    surfaceId: 2,
    membershipId: null,
    joinRequestId: 9,
}

const rejectedResult: JoinResultView = {
    status: 'REJECTED',
    surfaceType: 'GROUP',
    surfaceId: 2,
    membershipId: null,
    joinRequestId: 9,
}

const cancelledResult: JoinResultView = {
    status: 'CANCELLED',
    surfaceType: 'GROUP',
    surfaceId: 2,
    membershipId: null,
    joinRequestId: 9,
}

describe('deriveImJoinApplyState', () => {
    test('selects apply, pending cancel, reviewer, and leave actions from membership state', () => {
        expect(deriveImJoinApplyState({
            joinPolicy: 'OPEN',
            membershipStatus: null,
            memberRole: null,
        }).primaryAction).toBe('apply')
        expect(deriveImJoinApplyState({
            joinPolicy: 'APPROVAL',
            membershipStatus: 'PENDING',
            memberRole: null,
            pendingRequestId: 7,
        }).primaryAction).toBe('cancel')
        expect(deriveImJoinApplyState({
            joinPolicy: 'APPROVAL',
            membershipStatus: 'ACTIVE',
            memberRole: 'OWNER',
            pendingReviewRequests: [{requestId: 3, userLabel: 'Mario'}],
        }).reviewActions).toHaveLength(1)
        expect(deriveImJoinApplyState({
            joinPolicy: 'OPEN',
            membershipStatus: 'ACTIVE',
            memberRole: 'MEMBER',
        }).primaryAction).toBe('leave')
    })

    test('renders rejoin and reapply capable statuses as apply actions outside invite-only surfaces', () => {
        expect(deriveImJoinApplyState({
            joinPolicy: 'OPEN',
            membershipStatus: 'LEFT',
            memberRole: null,
        })).toMatchObject({
            primaryAction: 'apply',
            primaryText: 'Join',
            statusText: 'Open',
        })
        expect(deriveImJoinApplyState({
            joinPolicy: 'APPROVAL',
            membershipStatus: rejectedResult.status,
            memberRole: null,
        })).toMatchObject({
            primaryAction: 'apply',
            primaryText: 'Apply to join',
            statusText: 'Approval',
        })
        expect(deriveImJoinApplyState({
            joinPolicy: 'APPROVAL',
            membershipStatus: cancelledResult.status,
            memberRole: null,
        })).toMatchObject({
            primaryAction: 'apply',
            primaryText: 'Apply to join',
            statusText: 'Approval',
        })
    })

    test('keeps banned memberships non-actionable', () => {
        expect(deriveImJoinApplyState({
            joinPolicy: 'OPEN',
            membershipStatus: 'BANNED',
            memberRole: null,
        })).toMatchObject({
            primaryAction: 'none',
            statusText: 'Banned',
        })
    })
})

describe('resolveSelfJoinResult', () => {
    test('applies backend join results only for self membership actions', () => {
        expect(resolveSelfJoinResult('apply', pendingResult)).toBe(pendingResult)
        expect(resolveSelfJoinResult('cancel', cancelledResult)).toBe(cancelledResult)
        expect(resolveSelfJoinResult('approve', activeResult)).toBeUndefined()
        expect(resolveSelfJoinResult('reject', rejectedResult)).toBeUndefined()
    })
})

describe('surface-scoped local state', () => {
    test('uses a local join result only for the matching surface', () => {
        const scopedResult = {
            result: activeResult,
            surfaceKey: 'CHANNEL:1',
        }

        expect(resolveScopedJoinResult(scopedResult, {surfaceType: 'CHANNEL', surfaceId: 1})).toBe(activeResult)
        expect(resolveScopedJoinResult(scopedResult, {surfaceType: 'GROUP', surfaceId: 2})).toBeUndefined()
    })

    test('uses reviewed request ids only for the matching surface', () => {
        const reviewedRequestIds = {
            requestIds: [3],
            surfaceKey: 'GROUP:2',
        }

        expect(resolveScopedReviewedRequestIds(reviewedRequestIds, {surfaceType: 'GROUP', surfaceId: 2})).toEqual([3])
        expect(resolveScopedReviewedRequestIds(reviewedRequestIds, {surfaceType: 'CHANNEL', surfaceId: 1})).toEqual([])
    })
})

describe('ImJoinApplyControls', () => {
    test('renders open join and approval pending states', () => {
        const openMarkup = renderToStaticMarkup(
            <ImJoinApplyControls
                currentMemberRole={null}
                currentMembershipStatus={null}
                joinPolicy="OPEN"
                onApply={vi.fn().mockResolvedValue(activeResult)}
                onCancel={vi.fn()}
                onLeave={vi.fn()}
                onApprove={vi.fn()}
                onReject={vi.fn()}
                surface={{surfaceType: 'CHANNEL', surfaceId: 1}}
            />,
        )
        const pendingMarkup = renderToStaticMarkup(
            <ImJoinApplyControls
                currentMemberRole={null}
                currentMembershipStatus="PENDING"
                joinPolicy="APPROVAL"
                onApply={vi.fn().mockResolvedValue(pendingResult)}
                onCancel={vi.fn()}
                onLeave={vi.fn()}
                onApprove={vi.fn()}
                onReject={vi.fn()}
                pendingRequestId={9}
                surface={{surfaceType: 'GROUP', surfaceId: 2}}
            />,
        )

        expect(openMarkup).toContain('Join')
        expect(openMarkup).toContain('Open')
        expect(pendingMarkup).toContain('Request pending')
        expect(pendingMarkup).toContain('Cancel request')
    })

    test('renders reviewer controls for owner/admin pending requests and leave for active members', () => {
        const reviewMarkup = renderToStaticMarkup(
            <ImJoinApplyControls
                currentMemberRole="ADMIN"
                currentMembershipStatus="ACTIVE"
                joinPolicy="APPROVAL"
                onApply={vi.fn()}
                onCancel={vi.fn()}
                onLeave={vi.fn()}
                onApprove={vi.fn()}
                onReject={vi.fn()}
                pendingReviewRequests={[{requestId: 3, userLabel: 'Mario'}]}
                surface={{surfaceType: 'GROUP', surfaceId: 2}}
            />,
        )
        const leaveMarkup = renderToStaticMarkup(
            <ImJoinApplyControls
                currentMemberRole="MEMBER"
                currentMembershipStatus="ACTIVE"
                joinPolicy="OPEN"
                onApply={vi.fn()}
                onCancel={vi.fn()}
                onLeave={vi.fn()}
                onApprove={vi.fn()}
                onReject={vi.fn()}
                surface={{surfaceType: 'CHANNEL', surfaceId: 1}}
            />,
        )

        expect(reviewMarkup).toContain('Mario')
        expect(reviewMarkup).toContain('Approve')
        expect(reviewMarkup).toContain('Reject')
        expect(leaveMarkup).toContain('Leave')
        expect(leaveMarkup).not.toContain('Approve')
    })
})
