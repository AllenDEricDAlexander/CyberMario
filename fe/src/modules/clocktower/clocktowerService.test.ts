import {describe, expect, it, vi} from 'vitest'
import {
    advanceClocktowerFlow,
    claimClocktowerSeat,
    closeClocktowerNomination,
    confirmClocktowerExecution,
    createClocktowerInvitation,
    createClocktowerRoom,
    createClocktowerRuling,
    declineClocktowerInvitation,
    enterClocktowerRoom,
    generateClocktowerBoard,
    getClocktowerGameAudit,
    getClocktowerGameReplay,
    getClocktowerGameView,
    getClocktowerGroupedNightOrder,
    getClocktowerFlow,
    getClocktowerJinxRules,
    getClocktowerReplayVotes,
    getClocktowerRoomAudit,
    getClocktowerScripts,
    getClocktowerTerms,
    joinClocktowerRoom,
    listClocktowerAdminChatMessages,
    listClocktowerBoards,
    listClocktowerChatConversations,
    listClocktowerChatMessages,
    listClocktowerGameHistory,
    listClocktowerRulings,
    heartbeatClocktowerRoom,
    kickClocktowerRoomMember,
    markClocktowerChatRead,
    saveClocktowerBoard,
    sendClocktowerChatMessage,
    skipClocktowerNightTask,
    startClocktowerGame,
    streamClocktowerEvents,
    submitClocktowerStorytellerAction,
    acceptClocktowerInvitation,
    closeClocktowerMicSession,
    extendClocktowerMicSession,
    finishClocktowerMicTurn,
    getClocktowerAgentMemory,
    getClocktowerAgentTasks,
    getClocktowerGameAgents,
    getClocktowerMicSession,
    getClocktowerNightTasks,
    grabClocktowerMic,
    pauseClocktowerAgent,
    randomChoiceClocktowerNightTask,
    releaseClocktowerMic,
    resolveClocktowerNightTask,
    resumeClocktowerAgent,
    runClocktowerAgentNow,
    skipClocktowerGameNightTask,
    skipClocktowerMicTurn,
    startClocktowerDayMic,
    submitClocktowerGameAction,
    undoClocktowerRuling,
    validateClocktowerBoard,
    startClocktowerRoom,
} from './clocktowerService'

vi.mock('../../services/request', () => ({
    requestJson: vi.fn(),
    streamServerSentEvents: vi.fn(),
}))

vi.mock('../im/imService', () => ({
    listImConversations: vi.fn().mockResolvedValue([
        {
            id: 13,
            conversationType: 'GROUP',
            ownerSurfaceType: 'GROUP',
            ownerSurfaceId: 4,
            contextType: 'CLOCKTOWER_ROOM',
            contextId: 7,
            messageSeq: 9,
            lastMessage: null,
            lastMessageAt: '2026-06-24T10:00:00Z',
            lastActiveAt: '2026-06-24T10:00:00Z',
            status: 'ACTIVE',
            unreadCount: 2,
        },
    ]),
    listImMessages: vi.fn().mockResolvedValue({
        records: [
            {
                id: 21,
                conversationId: 13,
                senderUserId: 101,
                messageSeq: 9,
                messageType: 'TEXT',
                content: 'hello',
                status: 'SENT',
                sentAt: '2026-06-24T10:00:00Z',
            },
        ],
        page: 1,
        size: 20,
        total: 1,
        totalPages: 1,
    }),
    markImRead: vi.fn().mockResolvedValue({
        conversationId: 13,
        userId: 101,
        lastReadSeq: 9,
        unreadCount: 0,
    }),
    sendImMessage: vi.fn().mockResolvedValue({
        id: 22,
        conversationId: 13,
        senderUserId: 101,
        messageSeq: 10,
        clientMsgId: 'client-1',
        messageType: 'TEXT',
        content: 'hello',
        status: 'SENT',
        sentAt: '2026-06-24T10:01:00Z',
    }),
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

    it('enters rooms through the room lobby endpoint', async () => {
        const {requestJson} = await import('../../services/request')

        await enterClocktowerRoom(7)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/enter', {method: 'POST'})
    })

    it('sends room heartbeats through the room lobby endpoint', async () => {
        const {requestJson} = await import('../../services/request')

        await heartbeatClocktowerRoom(7)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/heartbeat', {method: 'POST'})
    })

    it('claims seats with the lobby seat claim request', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {displayName: 'Alice'}

        await claimClocktowerSeat(7, 3, request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/seats/3/claim', {
            method: 'POST',
            body: request,
        })
    })

    it('creates room invitations through the lobby endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            inviteeUserId: 31,
            invitationType: 'SEAT',
            targetSeatNo: 3,
            expiresAt: null,
        }

        await createClocktowerInvitation(7, request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/invitations', {
            method: 'POST',
            body: request,
        })
    })

    it('creates rooms with open seating policy payloads', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            name: '钟楼房间',
            scriptCode: 'TROUBLE_BREWING' as const,
            playerCount: 5,
            boardConfigId: null,
            boardCode: null,
            roleCodes: [],
            storytellerMode: 'HUMAN',
            allowSpectators: true,
            allowPrivateChat: true,
            agentSeatCount: 0,
            seatingPolicy: 'OPEN_SEATING',
        }

        await createClocktowerRoom(request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms', {
            method: 'POST',
            body: request,
        })
    })

    it('accepts and declines room invitations through the lobby endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        await acceptClocktowerInvitation(7, 11)
        await declineClocktowerInvitation(7, 12)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/invitations/11/accept', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/invitations/12/decline', {method: 'POST'})
    })

    it('kicks room members through the lobby endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {ban: true, banDurationSeconds: 3600, reason: 'blocked'}

        await kickClocktowerRoomMember(7, 31, request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/members/31/kick', {
            method: 'POST',
            body: request,
        })
    })

    it('starts games through the game lifecycle endpoint', async () => {
        const {requestJson} = await import('../../services/request')

        await startClocktowerGame(7)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/games/start', {
            method: 'POST',
        })
    })

    it('starts rooms through the assignment-based room endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {assignments: [{seatId: 31, roleCode: 'CHEF'}], randomize: false}

        await startClocktowerRoom(7, request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/rooms/7/start', {
            method: 'POST',
            body: request,
        })
    })

    it('loads game view by game id', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerGameView(11)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/view')
    })

    it('loads game replay and history endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerGameReplay(11)
        await listClocktowerGameHistory()

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/replay')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/history')
    })

    it('submits new game actions through the game endpoint', async () => {
        const {requestJson} = await import('../../services/request')
        const request = {
            actorGameSeatId: 31,
            actionType: 'PUBLIC_SPEECH',
            targetGameSeatIds: [],
            nominationId: null,
            vote: null,
            content: '我先报信息。',
            payload: {},
        }

        await submitClocktowerGameAction(11, request)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/actions', {
            method: 'POST',
            body: request,
        })
    })

    it('uses game mic endpoints for public mic actions', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerMicSession(11)
        await startClocktowerDayMic(11)
        await finishClocktowerMicTurn(11, 91)
        await skipClocktowerMicTurn(11, 92)
        await grabClocktowerMic(11)
        await releaseClocktowerMic(11)
        await extendClocktowerMicSession(11, 120)
        await closeClocktowerMicSession(11)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/start-day', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/turns/91/finish', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/turns/92/skip', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/grab', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/release', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/extend', {
            method: 'POST',
            body: {seconds: 120},
        })
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/mic/close', {method: 'POST'})
    })

    it('uses storyteller agent control endpoints', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerGameAgents(11)
        await pauseClocktowerAgent(11, 81)
        await resumeClocktowerAgent(11, 81)
        await runClocktowerAgentNow(11, 81)
        await getClocktowerAgentMemory(11, 81)
        await getClocktowerAgentTasks(11, 81)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/pause', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/resume', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/run-now', {method: 'POST'})
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/memory')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/agents/81/tasks')
    })

    it('uses game night task endpoints', async () => {
        const {requestJson} = await import('../../services/request')
        const resolveRequest = {targetGameSeatIds: [31], payload: {}, note: 'override'}

        await getClocktowerNightTasks(11)
        await resolveClocktowerNightTask(11, 91, resolveRequest)
        await skipClocktowerGameNightTask(11, 91, {reason: 'manual skip'})
        await randomChoiceClocktowerNightTask(11, 91)

        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks')
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks/91/resolve', {
            method: 'POST',
            body: resolveRequest,
        })
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks/91/skip', {
            method: 'POST',
            body: {reason: 'manual skip'},
        })
        expect(requestJson).toHaveBeenCalledWith('/api/clocktower/games/11/night-tasks/91/random-choice', {method: 'POST'})
    })

    it('loads room chat conversations', async () => {
        const {listImConversations} = await import('../im/imService')

        const result = await listClocktowerChatConversations(7)

        expect(listImConversations).toHaveBeenCalledWith({contextType: 'CLOCKTOWER_ROOM', contextId: 7})
        expect(result[0]).toMatchObject({
            conversationId: 13,
            roomId: 7,
            gameId: 7,
            messageSeq: 9,
            unreadCount: 2,
        })
    })

    it('loads chat messages with default and overridden pagination', async () => {
        const {listImMessages} = await import('../im/imService')

        const result = await listClocktowerChatMessages(13)
        await listClocktowerChatMessages(13, {page: 3, size: 50})

        expect(listImMessages).toHaveBeenCalledWith(13, {})
        expect(listImMessages).toHaveBeenCalledWith(13, {page: 3, size: 50})
        expect(result.records[0]).toMatchObject({
            messageId: 21,
            conversationId: 13,
            messageSeq: 9,
            content: 'hello',
        })
    })

    it('sends chat messages to the conversation endpoint', async () => {
        const {sendImMessage} = await import('../im/imService')
        const request = {content: 'hello', metadataJson: '{"source":"test"}'}

        const result = await sendClocktowerChatMessage(13, request)
        const sendRequest = vi.mocked(sendImMessage).mock.calls.at(-1)?.[0]

        expect(sendRequest).toMatchObject({
            conversationId: 13,
            messageType: 'TEXT',
            content: 'hello',
            metadataJson: '{"source":"test"}',
        })
        expect(sendRequest?.clientMsgId).toMatch(/^clocktower-/)
        expect(result).toMatchObject({messageId: 22, clientMsgId: 'client-1'})
    })

    it('marks chat conversations read with the read state request', async () => {
        const {markImRead} = await import('../im/imService')
        const request = {messageSeq: 9}

        const result = await markClocktowerChatRead(13, request)

        expect(markImRead).toHaveBeenCalledWith(13, request)
        expect(result).toMatchObject({
            conversationId: 13,
            userId: 101,
            lastReadMessageSeq: 9,
        })
    })

    it('loads audit endpoints through the admin API prefix', async () => {
        const {requestJson} = await import('../../services/request')

        await getClocktowerRoomAudit(7)
        await getClocktowerGameAudit(11)

        expect(requestJson).toHaveBeenCalledWith('/api/admin/clocktower/rooms/7/audit')
        expect(requestJson).toHaveBeenCalledWith('/api/admin/clocktower/games/11/audit')
    })

    it('loads admin chat messages with default and overridden pagination', async () => {
        const {requestJson} = await import('../../services/request')

        await listClocktowerAdminChatMessages(13)
        await listClocktowerAdminChatMessages(13, {page: 4, size: 100})

        expect(requestJson).toHaveBeenCalledWith(
            '/api/admin/clocktower/chat/conversations/13/messages?page=1&size=20',
        )
        expect(requestJson).toHaveBeenCalledWith(
            '/api/admin/clocktower/chat/conversations/13/messages?page=4&size=100',
        )
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
