import {describe, expect, it, vi} from 'vitest'
import {
    advanceClocktowerFlow,
    closeClocktowerNomination,
    confirmClocktowerExecution,
    createClocktowerRuling,
    generateClocktowerBoard,
    getClocktowerGroupedNightOrder,
    getClocktowerFlow,
    getClocktowerJinxRules,
    getClocktowerReplayVotes,
    getClocktowerScripts,
    getClocktowerTerms,
    joinClocktowerRoom,
    listClocktowerBoards,
    listClocktowerRulings,
    saveClocktowerBoard,
    skipClocktowerNightTask,
    streamClocktowerEvents,
    submitClocktowerStorytellerAction,
    undoClocktowerRuling,
    validateClocktowerBoard,
} from './clocktowerService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
    streamServerSentEvents: vi.fn(),
}))

describe('clocktowerService', () => {
    it('loads script list from the clocktower script endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        await getClocktowerScripts()
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/scripts')
    })

    it('loads terms with optional filters', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerTerms({keyword: 'poison', category: 'status'})

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/terms?keyword=poison&category=status')
    })

    it('loads jinx rules with optional filters', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerJinxRules({roleCode: 'CHEF', severity: 'INFO'})

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/jinx-rules?roleCode=CHEF&severity=INFO')
    })

    it('loads grouped night order for a script', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerGroupedNightOrder('TROUBLE_BREWING')

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/scripts/TROUBLE_BREWING/night-order/grouped')
    })

    it('generates boards with POST body', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            scriptCode: 'TROUBLE_BREWING' as const,
            playerCount: 5,
            difficulty: 2,
            chaos: 2,
            evilPressure: 2,
            newbieFriendly: true,
            candidateCount: 2,
            lockedRoleCodes: ['CHEF'],
            bannedRoleCodes: ['POISONER'],
            seed: 'seed-1',
        }
        await generateClocktowerBoard(request)
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards/generate', {
            method: 'POST',
            body: request,
        })
    })

    it('validates boards with POST body', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            scriptCode: 'TROUBLE_BREWING' as const,
            playerCount: 5,
            roleCodes: ['CHEF', 'IMP'],
        }

        await validateClocktowerBoard(request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards/validate', {
            method: 'POST',
            body: request,
        })
    })

    it('saves boards with backend roleCodes payload', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            scriptCode: 'TROUBLE_BREWING' as const,
            playerCount: 5,
            difficulty: 2,
            chaos: 2,
            evilPressure: 2,
            newbieFriendly: true,
            seed: 'seed-1',
            roleCodes: ['CHEF', 'IMP'],
        }
        await saveClocktowerBoard(request)
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards/save', {
            method: 'POST',
            body: request,
        })
    })

    it('loads paged board library with filters', async () => {
        const {requestJson} = await import('../../services/request')

        await listClocktowerBoards({
            page: 3,
            size: 40,
            scriptCode: 'TROUBLE_BREWING',
            playerCount: 5,
            valid: true,
        })

        expect(requestJson).toHaveBeenCalledWith(
            '/api/clocktower/boards?page=3&size=40&scriptCode=TROUBLE_BREWING&playerCount=5&valid=true',
        )
    })

    it('loads valid saved boards for room creation', async () => {
        const {requestJson} = await import('../../services/request')

        await listClocktowerBoards({page: 1, size: 200, valid: true})

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards?page=1&size=200&valid=true')
    })

    it('joins rooms with room join request and returns the seat response', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {seatNo: 3, displayName: 'Alice', inviteCode: 'INVITE'}
        await joinClocktowerRoom(7, request)
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/join', {
            method: 'POST',
            body: request,
        })
    })

    it('submits storyteller actions to the storyteller endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {actionType: 'ADVANCE_PHASE', targetSeatIds: [], note: 'start day', payload: {phase: 'DAY'}}
        await submitClocktowerStorytellerAction(7, request)
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/storyteller/actions', {
            method: 'POST',
            body: request,
        })
    })

    it('loads replay votes for a room', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerReplayVotes(7)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/replays/7/votes')
    })

    it('loads and advances room flow', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerFlow(7)
        await advanceClocktowerFlow(7)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/flow')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/flow/advance', {method: 'POST'})
    })

    it('submits flow control actions', async () => {
        const {requestJson} = await import('../../services/request')

        await skipClocktowerNightTask(7, 11, {reason: '无需唤醒'})
        await closeClocktowerNomination(7, 12, {note: '投票结束'})
        await confirmClocktowerExecution(7, {execute: true, deathPolicy: 'NO_CHANGE', note: '执行但未死亡'})

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/night-tasks/11/skip', {
            method: 'POST',
            body: {reason: '无需唤醒'},
        })
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/nominations/12/close', {
            method: 'POST',
            body: {note: '投票结束'},
        })
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/execution/confirm', {
            method: 'POST',
            body: {execute: true, deathPolicy: 'NO_CHANGE', note: '执行但未死亡'},
        })
    })

    it('creates room rulings through the ruling endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            rulingType: 'MARK_DEAD' as const,
            targetSeatId: 3,
            reason: 'NIGHT_DEATH' as const,
            note: '夜晚死亡',
            publicNote: '一名玩家死亡',
            visibility: 'PUBLIC' as const,
            force: false,
        }

        await createClocktowerRuling(7, request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/rulings', {
            method: 'POST',
            body: request,
        })
    })

    it('loads and undoes room rulings', async () => {
        const {requestJson} = await import('../../services/request')

        await listClocktowerRulings(7)
        await undoClocktowerRuling(7, 9, {note: '误操作撤销', force: true})

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/rulings')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/rulings/9/undo', {
            method: 'POST',
            body: {note: '误操作撤销', force: true},
        })
    })

    it('streams room events with query parameters', async () => {
        const {streamServerSentEvents} = await import('../../services/request')
        const signal = new AbortController().signal
        const onEvent = vi.fn()
        await streamClocktowerEvents(7, {seatId: 3, lastEventSeq: 10}, signal, onEvent)
        expect(streamServerSentEvents).toHaveBeenCalledWith(
            '/api/clocktower/rooms/7/events/stream?seatId=3&lastEventSeq=10',
            {signal},
            onEvent,
        )
    })
})
