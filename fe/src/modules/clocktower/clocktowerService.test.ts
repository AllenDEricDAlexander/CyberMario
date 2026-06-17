import {describe, expect, it, vi} from 'vitest'
import {
    generateClocktowerBoard,
    joinClocktowerRoom,
    saveClocktowerBoard,
    submitClocktowerStorytellerAction,
    getClocktowerScripts,
    streamClocktowerEvents,
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
            lockedRoleCodes: ['washerwoman'],
            bannedRoleCodes: ['baron'],
            seed: 'seed-1',
        }
        await generateClocktowerBoard(request)
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards/generate', {
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
            roleCodes: ['washerwoman', 'imp'],
            validation: {
                valid: true,
                roleTypeCounts: {TOWNSFOLK: 3, OUTSIDER: 0, MINION: 1, DEMON: 1},
                violations: [],
                scores: [],
            },
        }
        await saveClocktowerBoard(request)
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/boards/save', {
            method: 'POST',
            body: request,
        })
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
