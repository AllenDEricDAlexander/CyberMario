import {requestJson, streamServerSentEvents} from '../../services/request'
import {buildSearchParams} from '../../services/urlSearch'
import type {
    BoardValidationResponse,
    ClocktowerActionResponse,
    ClocktowerBoardConfigResponse,
    ClocktowerBoardGenerateRequest,
    ClocktowerBoardGenerateResponse,
    ClocktowerBoardQuery,
    ClocktowerBoardSaveRequest,
    ClocktowerBoardValidateRequest,
    ClocktowerEventResponse,
    ClocktowerFlowResponse,
    ClocktowerGrimoireResponse,
    ClocktowerJinxRuleResponse,
    ClocktowerNightChecklistResponse,
    ClocktowerNightOrderGroupResponse,
    ClocktowerNightOrderResponse,
    ClocktowerPage,
    ClocktowerPlayerActionRequest,
    ClocktowerPlayerViewResponse,
    ClocktowerReplayResponse,
    ClocktowerRoleResponse,
    ClocktowerRoleTypeCode,
    ClocktowerRoomCreateRequest,
    ClocktowerRoomJoinRequest,
    ClocktowerRoomResponse,
    ClocktowerRoomStartRequest,
    ClocktowerRulingApplyResponse,
    ClocktowerRulingCreateRequest,
    ClocktowerRulingResponse,
    ClocktowerRulingUndoRequest,
    ClocktowerScriptCode,
    ClocktowerScriptResponse,
    ClocktowerSeatResponse,
    ClocktowerStartGameResponse,
    ClocktowerStorytellerActionRequest,
    ClocktowerTermResponse,
    ClocktowerUpdateSeatRequest,
    CloseNominationRequest,
    ExecutionConfirmRequest,
    SkipNightTaskRequest,
    ClocktowerVoteReplayResponse,
    StorytellerActionResponse,
} from './clocktowerTypes'

export function getClocktowerScripts() {
    return requestJson<ClocktowerScriptResponse[]>('/api/clocktower/scripts')
}

export function getClocktowerScript(scriptCode: ClocktowerScriptCode) {
    return requestJson<ClocktowerScriptResponse>(`/api/clocktower/scripts/${scriptCode}`)
}

export function getClocktowerRoles(
    scriptCode: ClocktowerScriptCode,
    params: { roleType?: ClocktowerRoleTypeCode; enabled?: boolean } = {},
) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerRoleResponse[]>(`/api/clocktower/scripts/${scriptCode}/roles${suffix(search)}`)
}

export function getClocktowerNightOrder(scriptCode: ClocktowerScriptCode, params: { nightType?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerNightOrderResponse[]>(`/api/clocktower/scripts/${scriptCode}/night-order${suffix(search)}`)
}

export function getClocktowerGroupedNightOrder(scriptCode: ClocktowerScriptCode) {
    return requestJson<ClocktowerNightOrderGroupResponse>(`/api/clocktower/scripts/${scriptCode}/night-order/grouped`)
}

export function getClocktowerTerms(params: { keyword?: string; category?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerTermResponse[]>(`/api/clocktower/terms${suffix(search)}`)
}

export function getClocktowerJinxRules(params: { roleCode?: string; severity?: string } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerJinxRuleResponse[]>(`/api/clocktower/jinx-rules${suffix(search)}`)
}

export function generateClocktowerBoard(request: ClocktowerBoardGenerateRequest) {
    return requestJson<ClocktowerBoardGenerateResponse>('/api/clocktower/boards/generate', {
        method: 'POST',
        body: request,
    })
}

export function validateClocktowerBoard(request: ClocktowerBoardValidateRequest) {
    return requestJson<BoardValidationResponse>('/api/clocktower/boards/validate', {
        method: 'POST',
        body: request,
    })
}

export function saveClocktowerBoard(request: ClocktowerBoardSaveRequest) {
    return requestJson<ClocktowerBoardConfigResponse>('/api/clocktower/boards/save', {
        method: 'POST',
        body: request,
    })
}

export function listClocktowerBoards(params: ClocktowerBoardQuery = {}) {
    const search = buildSearchParams({
        page: params.page ?? 1,
        size: params.size ?? 20,
        scriptCode: params.scriptCode,
        playerCount: params.playerCount,
        valid: params.valid,
    })
    return requestJson<ClocktowerPage<ClocktowerBoardConfigResponse>>(`/api/clocktower/boards${suffix(search)}`)
}

export function deleteClocktowerBoard(boardId: number) {
    return requestJson<void>(`/api/clocktower/boards/${boardId}`, {method: 'DELETE'})
}

export function createClocktowerRoom(request: ClocktowerRoomCreateRequest) {
    return requestJson<ClocktowerRoomResponse>('/api/clocktower/rooms', {
        method: 'POST',
        body: request,
    })
}

export function listClocktowerRooms() {
    return requestJson<ClocktowerRoomResponse[]>('/api/clocktower/rooms')
}

export function getClocktowerRoom(roomId: number) {
    return requestJson<ClocktowerRoomResponse>(`/api/clocktower/rooms/${roomId}`)
}

export function joinClocktowerRoom(roomId: number, request: ClocktowerRoomJoinRequest) {
    return requestJson<ClocktowerSeatResponse>(`/api/clocktower/rooms/${roomId}/join`, {
        method: 'POST',
        body: request,
    })
}

export function leaveClocktowerRoom(roomId: number) {
    return requestJson<void>(`/api/clocktower/rooms/${roomId}/leave`, {method: 'POST'})
}

export function updateClocktowerSeat(roomId: number, seatId: number, request: ClocktowerUpdateSeatRequest) {
    return requestJson<ClocktowerRoomResponse>(`/api/clocktower/rooms/${roomId}/seats/${seatId}`, {
        method: 'PATCH',
        body: request,
    })
}

export function startClocktowerGame(roomId: number, request: ClocktowerRoomStartRequest) {
    return requestJson<ClocktowerStartGameResponse>(`/api/clocktower/rooms/${roomId}/start`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerGrimoire(roomId: number) {
    return requestJson<ClocktowerGrimoireResponse>(`/api/clocktower/rooms/${roomId}/grimoire`)
}

export function getClocktowerNightChecklist(roomId: number) {
    return requestJson<ClocktowerNightChecklistResponse>(`/api/clocktower/rooms/${roomId}/night-checklist`)
}

export function getClocktowerFlow(roomId: number) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/flow`)
}

export function advanceClocktowerFlow(roomId: number) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/flow/advance`, {method: 'POST'})
}

export function skipClocktowerNightTask(roomId: number, taskId: number, request: SkipNightTaskRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/night-tasks/${taskId}/skip`, {
        method: 'POST',
        body: request,
    })
}

export function closeClocktowerNomination(roomId: number, nominationId: number, request: CloseNominationRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/nominations/${nominationId}/close`, {
        method: 'POST',
        body: request,
    })
}

export function confirmClocktowerExecution(roomId: number, request: ExecutionConfirmRequest) {
    return requestJson<ClocktowerFlowResponse>(`/api/clocktower/rooms/${roomId}/execution/confirm`, {
        method: 'POST',
        body: request,
    })
}

export function createClocktowerRuling(roomId: number, request: ClocktowerRulingCreateRequest) {
    return requestJson<ClocktowerRulingApplyResponse>(`/api/clocktower/rooms/${roomId}/rulings`, {
        method: 'POST',
        body: request,
    })
}

export function listClocktowerRulings(roomId: number) {
    return requestJson<ClocktowerRulingResponse[]>(`/api/clocktower/rooms/${roomId}/rulings`)
}

export function undoClocktowerRuling(roomId: number, rulingId: number, request: ClocktowerRulingUndoRequest) {
    return requestJson<ClocktowerRulingApplyResponse>(`/api/clocktower/rooms/${roomId}/rulings/${rulingId}/undo`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerPlayerView(roomId: number, params: { seatId?: number } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerPlayerViewResponse>(`/api/clocktower/rooms/${roomId}/view${suffix(search)}`)
}

export function submitClocktowerPlayerAction(roomId: number, request: ClocktowerPlayerActionRequest) {
    return requestJson<ClocktowerActionResponse>(`/api/clocktower/rooms/${roomId}/actions`, {
        method: 'POST',
        body: request,
    })
}

export function submitClocktowerStorytellerAction(roomId: number, request: ClocktowerStorytellerActionRequest) {
    return requestJson<StorytellerActionResponse>(`/api/clocktower/rooms/${roomId}/storyteller/actions`, {
        method: 'POST',
        body: request,
    })
}

export function getClocktowerReplay(roomId: number, params: { mode?: string; fromSeq?: number; toSeq?: number } = {}) {
    const search = buildSearchParams(params)
    return requestJson<ClocktowerReplayResponse>(`/api/clocktower/replays/${roomId}${suffix(search)}`)
}

export function getClocktowerReplayVotes(roomId: number) {
    return requestJson<ClocktowerVoteReplayResponse[]>(`/api/clocktower/replays/${roomId}/votes`)
}

export function streamClocktowerEvents(
    roomId: number,
    params: { seatId?: number; lastEventSeq?: number },
    signal: AbortSignal,
    onEvent: (event: ClocktowerEventResponse) => void,
) {
    return streamServerSentEvents<ClocktowerEventResponse>(
        `/api/clocktower/rooms/${roomId}/events/stream?${buildSearchParams(params)}`,
        {signal},
        onEvent,
    )
}

function suffix(search: string) {
    return search ? `?${search}` : ''
}
