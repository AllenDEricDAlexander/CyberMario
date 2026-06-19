import type {PageResult} from '../../types/api'
import type {CodedEnum} from '../../utils/enum'

export type ClocktowerScriptCode = 'TROUBLE_BREWING' | 'BAD_MOON_RISING' | 'SECTS_AND_VIOLETS'
export type ClocktowerRoleTypeCode = 'TOWNSFOLK' | 'OUTSIDER' | 'MINION' | 'DEMON' | 'TRAVELER' | 'FABLED'
export type ClocktowerRoleType = ClocktowerRoleTypeCode | CodedEnum
export type ClocktowerAlignmentCode = 'GOOD' | 'EVIL' | 'NEUTRAL'
export type ClocktowerAlignment = ClocktowerAlignmentCode | CodedEnum
export type ClocktowerNightTypeCode = 'FIRST_NIGHT' | 'OTHER_NIGHT'
export type ClocktowerNightType = ClocktowerNightTypeCode | CodedEnum
export type ClocktowerRoomStatus = 'LOBBY' | 'SETUP' | 'RUNNING' | 'ENDED' | 'ARCHIVED'
export type ClocktowerPhase = 'LOBBY' | 'SETUP' | 'FIRST_NIGHT' | 'DAY' | 'NOMINATION' | 'EXECUTION' | 'NIGHT' | 'ENDED'
export type ClocktowerVisibility = 'PUBLIC' | 'PRIVATE' | 'STORYTELLER' | 'AUDIT'
export type ClocktowerViewerMode = 'PLAYER' | 'STORYTELLER' | 'ADMIN'
export type ClocktowerEventType =
    | 'ROOM_CREATED'
    | 'PLAYER_JOINED'
    | 'PLAYER_LEFT'
    | 'SEAT_UPDATED'
    | 'ROLE_ASSIGNED'
    | 'PHASE_CHANGED'
    | 'PUBLIC_MESSAGE_SENT'
    | 'PRIVATE_MESSAGE_SENT'
    | 'NIGHT_STEP_UPDATED'
    | 'NIGHT_ACTION_SUBMITTED'
    | 'PLAYER_NOMINATED'
    | 'VOTE_CAST'
    | 'DEAD_VOTE_SPENT'
    | 'PLAYER_EXECUTED'
    | 'PLAYER_DIED'
    | 'MARKER_ADDED'
    | 'MARKER_REMOVED'
    | 'STORYTELLER_RULING'
    | 'GAME_ENDED'
    | 'ACTION_REJECTED'

export type ClocktowerRulingType =
    | 'MARK_DEAD'
    | 'RESTORE_ALIVE'
    | 'SET_PUBLIC_LIFE'
    | 'EXECUTE_PLAYER'
    | 'SKIP_EXECUTION'
    | 'END_GAME'
    | 'ADVANCE_PHASE'
    | 'CLOSE_NOMINATION'
    | 'REOPEN_NOMINATION'
    | 'VOID_NOMINATION'
    | 'UNDO_RULING'

export type ClocktowerRulingCreateType = Exclude<ClocktowerRulingType, 'ADVANCE_PHASE' | 'UNDO_RULING'>

export type ClocktowerRulingReason =
    | 'VOTE_EXECUTION'
    | 'ROLE_ABILITY'
    | 'NIGHT_DEATH'
    | 'STORYTELLER_RULING'
    | 'PLAYER_REQUEST'
    | 'MISTAKE_FIX'
    | 'OTHER'

export type ClocktowerRulingStatus = 'APPLIED' | 'REVOKED'
export type ClocktowerLifeStatus = 'ALIVE' | 'DEAD'
export type ClocktowerPage<T> = PageResult<T>

export type ClocktowerRulingCreateRequest = {
    rulingType: ClocktowerRulingCreateType
    targetSeatId?: number | null
    nominationId?: number | null
    targetPhase?: ClocktowerPhase | null
    publicLifeStatus?: ClocktowerLifeStatus | null
    winner?: 'GOOD' | 'EVIL' | null
    reason: ClocktowerRulingReason
    note: string
    publicNote?: string | null
    visibility: ClocktowerVisibility
    force: boolean
}

export type ClocktowerRulingUndoRequest = {
    note: string
    force: boolean
}

export type ClocktowerRulingResponse = {
    rulingId: number
    roomId: number
    rulingType: ClocktowerRulingType
    status: ClocktowerRulingStatus
    targetSeatId?: number | null
    nominationId?: number | null
    targetPhase?: ClocktowerPhase | null
    publicLifeStatus?: string | null
    winner?: string | null
    reason: ClocktowerRulingReason
    note: string
    publicNote?: string | null
    visibility: ClocktowerVisibility
    undoOfRulingId?: number | null
}

export type ClocktowerRulingApplyResponse = {
    ruling: ClocktowerRulingResponse
    grimoire: ClocktowerGrimoireResponse
    events: ClocktowerEventResponse[]
}

export type ClocktowerScriptResponse = {
    scriptCode: ClocktowerScriptCode
    name: string
    edition: string
    minPlayers: number
    maxPlayers: number
    roleCount: number
    enabled: boolean
    sourceUrl?: string | null
}

export type ClocktowerRoleResponse = {
    scriptCode: ClocktowerScriptCode
    roleCode: string
    roleType: ClocktowerRoleType
    roleName: string
    name: string
    alignment: ClocktowerAlignment
    abilityText: string
    firstNightOrder?: number | null
    otherNightOrder?: number | null
    firstNightReminder?: string | null
    otherNightReminder?: string | null
    enabled: boolean
    sourceUrl?: string | null
}

export type ClocktowerNightOrderResponse = {
    scriptCode: ClocktowerScriptCode
    roleCode: string
    roleName: string
    roleType: ClocktowerRoleType
    nightType: ClocktowerNightType
    orderNo: number
    sortOrder: number
    reminderText?: string | null
}

export type ClocktowerNightOrderGroupResponse = {
    firstNight: ClocktowerNightOrderResponse[]
    otherNight: ClocktowerNightOrderResponse[]
}

export type ClocktowerRoleSummaryResponse = {
    scriptCode?: ClocktowerScriptCode
    roleCode: string
    roleName: string
    roleType?: ClocktowerRoleType | null
    alignment?: ClocktowerAlignment | null
}

export type ClocktowerTermResponse = {
    term: string
    category: string
    description: string
    sourceUrl?: string | null
}

export type ClocktowerJinxRuleResponse = {
    roleACode: string
    roleBCode: string
    scope: string
    severity: string
    effectType: string
    ruleText: string
    sourceUrl?: string | null
}

export type ClocktowerBoardGenerateRequest = {
    scriptCode: ClocktowerScriptCode
    playerCount: number
    difficulty: number
    chaos: number
    evilPressure: number
    newbieFriendly: boolean
    candidateCount: number
    lockedRoleCodes?: string[]
    bannedRoleCodes?: string[]
    seed?: string | null
}

export type ClocktowerBoardValidateRequest = {
    scriptCode: ClocktowerScriptCode
    playerCount: number
    roleCodes: string[]
}

export type ClocktowerBoardQuery = {
    scriptCode?: ClocktowerScriptCode
    playerCount?: number
    valid?: boolean
    page?: number
    size?: number
}

export type ClocktowerBoardSaveRequest = {
    scriptCode: ClocktowerScriptCode
    playerCount: number
    difficulty: number
    chaos: number
    evilPressure: number
    newbieFriendly: boolean
    seed?: string | null
    roleCodes: string[]
}

export type ClocktowerBoardGenerateResponse = {
    candidates: ClocktowerBoardCandidateResponse[]
}

export type ClocktowerBoardCandidateResponse = {
    candidateId: string
    scriptCode: ClocktowerScriptCode
    playerCount: number
    roleCodes: string[]
    roles?: ClocktowerRoleSummaryResponse[]
    validation: ClocktowerBoardValidationResponse
    scores: ClocktowerScoreResponse[]
}

export type BoardValidationResponse = {
    valid: boolean
    typeCounts: ClocktowerRoleTypeCountResponse
    issues: ClocktowerRuleViolationResponse[]
    scores: ClocktowerScoreResponse[]
}

export type ClocktowerBoardValidationResponse = {
    valid: boolean
    roleTypeCounts: Partial<Record<ClocktowerRoleTypeCode, number>>
    violations: ClocktowerRuleViolationResponse[]
    scores: ClocktowerScoreResponse[]
}

export type ClocktowerRoleTypeCountResponse = {
    townsfolk: number
    outsider: number
    minion: number
    demon: number
    traveler: number
    fabled: number
}

export type ClocktowerRuleViolationResponse = {
    code: string
    message: string
    severity: string
}

export type ClocktowerScoreResponse = {
    scoreType: string
    delta: number
    reason: string
}

export type ClocktowerBoardConfigResponse = {
    boardId: number
    boardCode: string
    scriptCode: ClocktowerScriptCode
    playerCount: number
    roleCodes: string[]
    roles?: ClocktowerRoleSummaryResponse[]
    validation: ClocktowerBoardValidationResponse
    valid: boolean
    createdAt?: string | null
}

export type ClocktowerRoomCreateRequest = {
    name: string
    scriptCode: ClocktowerScriptCode
    playerCount: number
    boardConfigId?: number | null
    boardCode?: string | null
    roleCodes: string[]
    storytellerMode: string
    allowSpectators: boolean
    allowPrivateChat: boolean
    agentSeatCount: number
}

export type ClocktowerCreateRoomRequest = ClocktowerRoomCreateRequest

export type ClocktowerRoomJoinRequest = {
    seatNo?: number | null
    displayName?: string | null
    inviteCode?: string | null
}

export type ClocktowerJoinRoomRequest = ClocktowerRoomJoinRequest

export type ClocktowerUpdateSeatRequest = {
    displayName?: string | null
    seatNo?: number | null
    roleCode?: string | null
}

export type ClocktowerRoomStartRequest = {
    assignments: RoleAssignmentRequest[]
    randomize: boolean
}

export type RoleAssignmentRequest = {
    seatId: number
    roleCode: string
}

export type ClocktowerRoomResponse = {
    roomId: number
    roomCode: string
    name: string
    scriptCode: ClocktowerScriptCode
    status: ClocktowerRoomStatus
    phase: ClocktowerPhase
    playerCount: number
    storytellerUserId?: number | null
    seats: ClocktowerSeatResponse[]
}

export type ClocktowerSeatResponse = {
    seatId: number
    seatNo: number
    userId?: number | null
    displayName: string
    roleCode?: string | null
    roleType?: ClocktowerRoleType | null
    lifeStatus: string
    publicLifeStatus: string
    connected: boolean
    hasDeadVote: boolean
}

export type ClocktowerStartGameResponse = {
    roomId: number
    status: ClocktowerRoomStatus
    phase: ClocktowerPhase
}

export type GamePhaseResponse = {
    phase: ClocktowerPhase
    dayNo: number
    nightNo: number
}

export type ClocktowerFlowTransition =
    | 'COMPLETE_FIRST_NIGHT'
    | 'START_NOMINATION'
    | 'START_EXECUTION'
    | 'START_NIGHT'
    | 'COMPLETE_NIGHT'
    | 'NONE'

export type NightTaskSummaryResponse = {
    total: number
    pending: number
    done: number
    skipped: number
}

export type NominationSummaryResponse = {
    nominationId: number
    nominatorSeatId: number
    nomineeSeatId: number
    voteCount: number
    status: string
}

export type ExecutionCandidateResponse = {
    resolved: boolean
    executable: boolean
    nominationId?: number | null
    nomineeSeatId?: number | null
    voteCount: number
    threshold: number
    reason: string
}

export type VictoryCandidateResponse = {
    winner: 'GOOD' | 'EVIL'
    reason: string
}

export type ClocktowerFlowResponse = {
    roomId: number
    phase: GamePhaseResponse
    nextTransition: ClocktowerFlowTransition
    advanceAllowed: boolean
    blockingReasons: string[]
    nightTaskSummary: NightTaskSummaryResponse
    openNomination?: NominationSummaryResponse | null
    executionCandidate?: ExecutionCandidateResponse | null
    victoryCandidate?: VictoryCandidateResponse | null
}

export type SkipNightTaskRequest = {
    reason: string
}

export type CloseNominationRequest = {
    note?: string | null
}

export type ClocktowerExecutionDeathPolicy = 'NO_CHANGE' | 'MARK_DEAD'

export type ExecutionConfirmRequest = {
    execute: boolean
    deathPolicy: ClocktowerExecutionDeathPolicy
    note?: string | null
}

export type ClocktowerGrimoireResponse = {
    roomId: number
    phase: GamePhaseResponse
    seats: GrimoireSeatResponse[]
    markers: StatusMarkerResponse[]
    reminders: string[]
    pendingTasks: StorytellerTaskResponse[]
    ruleTraceEnabled: boolean
}

export type GrimoireSeatResponse = {
    seatId: number
    seatNo: number
    userId?: number | null
    displayName: string
    roleCode?: string | null
    roleType?: ClocktowerRoleType | null
    alignment?: ClocktowerAlignment | null
    alive: boolean
    publicAlive: boolean
    lifeStatus: string
    publicLifeStatus: string
    hasDeadVote: boolean
    connected: boolean
    notes?: string | null
}

export type StatusMarkerResponse = {
    markerId: number
    seatId?: number | null
    markerType: string
    markerName: string
    active: boolean
}

export type StorytellerTaskResponse = {
    taskId: number
    taskType: string
    phase: ClocktowerPhase
    roleCode?: string | null
    seatId?: number | null
    status: string
    note?: string | null
}

export type ClocktowerNightChecklistResponse = {
    nightNo: number
    nightType: ClocktowerNightType
    steps: NightStepResponse[]
    completed: boolean
}

export type NightStepResponse = {
    orderNo: number
    seatId?: number | null
    roleCode: string
    roleName: string
    roleType?: ClocktowerRoleType | null
    wakeRequired: boolean
    skipReason?: string | null
    completed: boolean
}

export type ClocktowerEventResponse = {
    eventId: number
    roomId: number
    seqNo: number
    eventType: ClocktowerEventType
    phase: ClocktowerPhase
    dayNo: number
    nightNo: number
    actorUserId?: number | null
    actorSeatId?: number | null
    targetSeatId?: number | null
    visibility: ClocktowerVisibility
    visibleSeatIds: number[]
    payload: Record<string, unknown>
    createdAt: string
}

export type ClocktowerPlayerViewResponse = {
    room: ClocktowerRoomResponse
    viewerMode: ClocktowerViewerMode
    mySeat?: PlayerSeatViewResponse | null
    publicSeats: PublicSeatResponse[]
    phase: GamePhaseResponse
    availableActions: AvailableActionResponse[]
    recentEvents: ClocktowerEventResponse[]
    privateThreads: PrivateThreadSummaryResponse[]
}

export type PlayerSeatViewResponse = {
    seatId: number
    seatNo: number
    displayName: string
    roleCode?: string | null
    roleType?: ClocktowerRoleType | null
    alignment?: ClocktowerAlignment | null
    lifeStatus: string
    publicLifeStatus: string
    hasDeadVote: boolean
}

export type PublicSeatResponse = {
    seatId: number
    seatNo: number
    displayName: string
    roleCode?: string | null
    lifeStatus: string
    connected: boolean
    hasDeadVote: boolean
}

export type AvailableActionResponse = {
    actionType: string
    label: string
    enabled: boolean
}

export type PrivateThreadSummaryResponse = {
    threadId: number
    seatId: number
    displayName: string
    unreadCount: number
}

export type ClocktowerPlayerActionRequest = {
    seatId: number
    actionType: string
    targetSeatIds: number[]
    privateThreadId?: number | null
    content?: string | null
    payload?: Record<string, unknown> | null
    clientActionId?: string | null
}

export type ClocktowerStorytellerActionRequest = {
    actionType: string
    targetSeatIds: number[]
    note?: string | null
    payload?: Record<string, unknown> | null
}

export type StorytellerActionRequest = ClocktowerStorytellerActionRequest

export type ClocktowerActionResponse = {
    accepted: boolean
    rejectedCode?: string | null
    event?: ClocktowerEventResponse | null
}

export type StorytellerActionResponse = {
    accepted: boolean
    rejectedCode?: string | null
    event?: ClocktowerEventResponse | null
    grimoire: ClocktowerGrimoireResponse
}

export type ClocktowerReplayResponse = {
    roomId: number
    mode: string
    events: ClocktowerEventResponse[]
}

export type ClocktowerVoteReplayResponse = {
    voteId: number
    nominationId: number
    voterSeatId: number
    voteValue: boolean
    usedDeadVote: boolean
    eventId?: number | null
}
