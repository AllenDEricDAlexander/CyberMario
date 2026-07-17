import {useCallback, useEffect, useMemo, useReducer, useRef} from 'react'
import {resolveErrorMessage} from '../../services/request'
import type {PageResult} from '../../types/api'
import {
    approveImJoinRequest,
    cancelImJoinRequest,
    createImDm,
    createImJoinRequest,
    leaveImSurface,
    listImMessages,
    markImRead,
    rejectImJoinRequest,
    sendImMessage,
} from './imService'
import type {
    ImMessagePushPayload,
    ImResyncPayload,
    ImSurfaceType,
    JoinRequestCreateRequest,
    JoinRequestRejectRequest,
    JoinResultView,
    MessageView,
    SendMessageRequest,
    UnreadView,
} from './imTypes'
import {
    acceptPlatformInvitation,
    acceptPlatformFriendRequest,
    cancelPlatformFriendRequest,
    createPlatformChannel,
    createPlatformChannelGroup,
    createPlatformFriendRequest,
    createPlatformGroup,
    getPlatformImBootstrap,
    invitePlatformSurface,
    listPlatformChannelGroups,
    listPlatformChannels,
    listPlatformFriendRequests,
    listPlatformFriends,
    listPlatformGroups,
    listPlatformInvitations,
    listPlatformImConversations,
    listPlatformJoinRequests,
    listPlatformSurfaceMembers,
    rejectPlatformInvitation,
    rejectPlatformFriendRequest,
    removePlatformFriend,
    removePlatformSurfaceMember,
    searchPlatformUsers,
    transferPlatformSurfaceOwnership,
    updatePlatformFriendRemark,
} from './platformImService'
import type {
    FriendRequestBox,
    OptimisticMessageView,
    PlatformBootstrapView,
    PlatformChannelGroupCreateRequest,
    PlatformChannelView,
    PlatformConversationView,
    PlatformFriendDecisionRequest,
    PlatformFriendPage,
    PlatformFriendRemarkRequest,
    PlatformFriendRequestCreateRequest,
    PlatformFriendRequestPage,
    PlatformFriendRequestView,
    PlatformFriendView,
    PlatformGroupView,
    PlatformImActivity,
    PlatformInvitationPage,
    PlatformInvitationView,
    PlatformJoinRequestPage,
    PlatformJoinRequestView,
    PlatformOwnershipTransferRequest,
    PlatformPageParams,
    PlatformSurfaceCreateRequest,
    PlatformSurfaceInvitationRequest,
    PlatformSurfaceMemberPage,
    PlatformSurfaceMemberView,
    PlatformUserPage,
    PlatformUserView,
} from './platformImTypes'
import {useImSocket} from './useImSocket'

type WorkspaceStatus = 'idle' | 'loading' | 'ready' | 'error'

export type PlatformImWorkspaceState = {
    activity: PlatformImActivity
    selectedConversationId?: number
    currentUser?: PlatformUserView
    conversations: PlatformConversationView[]
    messagesByConversation: Record<number, OptimisticMessageView[]>
    friends: PlatformFriendView[]
    incomingRequests: PlatformFriendRequestView[]
    outgoingRequests: PlatformFriendRequestView[]
    userResults: PlatformUserView[]
    channels: PlatformChannelView[]
    groups: PlatformGroupView[]
    invitations: PlatformInvitationView[]
    surfaceMembers: PlatformSurfaceMemberView[]
    joinRequests: PlatformJoinRequestView[]
    unreadTotal: number
    pendingFriendRequestCount: number
    pendingInvitationCount: number
    status: WorkspaceStatus
    error?: string
}

export type PlatformImWorkspaceAction =
    | {type: 'ACTIVITY_SELECTED'; activity: PlatformImActivity}
    | {type: 'LOADING'}
    | {type: 'FAILED'; error: string}
    | {type: 'BOOTSTRAPPED'; bootstrap: PlatformBootstrapView}
    | {type: 'CONVERSATIONS_LOADED'; conversations: PlatformConversationView[]}
    | {type: 'CONVERSATION_SELECTED'; conversationId: number}
    | {type: 'MESSAGES_MERGED'; conversationId: number; messages: OptimisticMessageView[]}
    | {type: 'MESSAGE_OPTIMISTIC'; message: OptimisticMessageView}
    | {type: 'MESSAGE_ACKNOWLEDGED'; message: MessageView}
    | {type: 'MESSAGE_FAILED'; conversationId: number; clientMsgId: string; error: string}
    | {type: 'MESSAGE_RETRYING'; conversationId: number; clientMsgId: string}
    | {type: 'UNREAD_UPDATED'; unread: UnreadView}
    | {type: 'FRIENDS_LOADED'; friends: PlatformFriendView[]}
    | {type: 'FRIEND_REQUESTS_LOADED'; box: FriendRequestBox; requests: PlatformFriendRequestView[]}
    | {type: 'USERS_LOADED'; users: PlatformUserView[]}
    | {type: 'CHANNELS_LOADED'; channels: PlatformChannelView[]}
    | {type: 'GROUPS_LOADED'; groups: PlatformGroupView[]}
    | {type: 'INVITATIONS_LOADED'; invitations: PlatformInvitationView[]}
    | {type: 'SURFACE_MEMBERS_LOADED'; members: PlatformSurfaceMemberView[]}
    | {type: 'JOIN_REQUESTS_LOADED'; requests: PlatformJoinRequestView[]}

export type PlatformImWorkspaceApi = {
    getBootstrap: () => Promise<PlatformBootstrapView>
    listConversations: () => Promise<PlatformConversationView[]>
    listMessages: typeof listImMessages
    markRead: typeof markImRead
    sendMessage: typeof sendImMessage
    openDm: typeof createImDm
    searchUsers: (keyword: string, params?: PlatformPageParams) => Promise<PlatformUserPage>
    listFriends: (params?: PlatformPageParams) => Promise<PlatformFriendPage>
    listFriendRequests: (box: FriendRequestBox, params?: PlatformPageParams) => Promise<PlatformFriendRequestPage>
    createFriendRequest: (request: PlatformFriendRequestCreateRequest) => Promise<PlatformFriendRequestView>
    acceptFriendRequest: (id: number, request?: PlatformFriendDecisionRequest) => Promise<PlatformFriendRequestView>
    rejectFriendRequest: (id: number, request?: PlatformFriendDecisionRequest) => Promise<PlatformFriendRequestView>
    cancelFriendRequest: (id: number) => Promise<PlatformFriendRequestView>
    updateFriendRemark: (friendUserId: number, request: PlatformFriendRemarkRequest) => Promise<PlatformFriendView>
    removeFriend: (friendUserId: number) => Promise<void>
    createChannel: (request: PlatformSurfaceCreateRequest) => Promise<PlatformChannelView>
    listChannels: () => Promise<PlatformChannelView[]>
    createGroup: (request: PlatformSurfaceCreateRequest) => Promise<PlatformGroupView>
    listGroups: () => Promise<PlatformGroupView[]>
    createChannelGroup: (channelId: number, request: PlatformChannelGroupCreateRequest) => Promise<PlatformGroupView>
    listChannelGroups: (channelId: number) => Promise<PlatformGroupView[]>
    listInvitations: (params?: PlatformPageParams) => Promise<PlatformInvitationPage>
    inviteSurface: (
        surfaceType: ImSurfaceType,
        surfaceId: number,
        request: PlatformSurfaceInvitationRequest,
    ) => Promise<PlatformInvitationView>
    acceptInvitation: (id: number) => Promise<PlatformInvitationView>
    rejectInvitation: (id: number) => Promise<PlatformInvitationView>
    transferOwnership: (
        surfaceType: ImSurfaceType,
        surfaceId: number,
        request: PlatformOwnershipTransferRequest,
    ) => Promise<void>
    applyJoin: (request: JoinRequestCreateRequest) => Promise<JoinResultView>
    approveJoin: (id: number) => Promise<JoinResultView>
    rejectJoin: (id: number, request?: JoinRequestRejectRequest) => Promise<JoinResultView>
    cancelJoin: (id: number) => Promise<JoinResultView>
    leaveSurface: typeof leaveImSurface
    listSurfaceMembers: (
        surfaceType: ImSurfaceType,
        surfaceId: number,
        params?: PlatformPageParams,
    ) => Promise<PlatformSurfaceMemberPage>
    listJoinRequests: (
        surfaceType: ImSurfaceType,
        surfaceId: number,
        params?: PlatformPageParams,
    ) => Promise<PlatformJoinRequestPage>
    removeSurfaceMember: (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => Promise<void>
}

export type UsePlatformImWorkspaceOptions = {
    api?: PlatformImWorkspaceApi
    realtimeEnabled?: boolean
}

const initialState: PlatformImWorkspaceState = {
    activity: 'MESSAGES',
    conversations: [],
    messagesByConversation: {},
    friends: [],
    incomingRequests: [],
    outgoingRequests: [],
    userResults: [],
    channels: [],
    groups: [],
    invitations: [],
    surfaceMembers: [],
    joinRequests: [],
    unreadTotal: 0,
    pendingFriendRequestCount: 0,
    pendingInvitationCount: 0,
    status: 'idle',
}

let optimisticMessageSequence = 0

export const defaultPlatformImWorkspaceApi: PlatformImWorkspaceApi = {
    getBootstrap: getPlatformImBootstrap,
    listConversations: listPlatformImConversations,
    listMessages: listImMessages,
    markRead: markImRead,
    sendMessage: sendImMessage,
    openDm: createImDm,
    searchUsers: (keyword, params = {}) => searchPlatformUsers({keyword, ...params}),
    listFriends: listPlatformFriends,
    listFriendRequests: (box, params = {}) => listPlatformFriendRequests({box, ...params}),
    createFriendRequest: createPlatformFriendRequest,
    acceptFriendRequest: acceptPlatformFriendRequest,
    rejectFriendRequest: rejectPlatformFriendRequest,
    cancelFriendRequest: cancelPlatformFriendRequest,
    updateFriendRemark: updatePlatformFriendRemark,
    removeFriend: removePlatformFriend,
    createChannel: createPlatformChannel,
    listChannels: listPlatformChannels,
    createGroup: createPlatformGroup,
    listGroups: listPlatformGroups,
    createChannelGroup: createPlatformChannelGroup,
    listChannelGroups: listPlatformChannelGroups,
    listInvitations: listPlatformInvitations,
    inviteSurface: invitePlatformSurface,
    acceptInvitation: acceptPlatformInvitation,
    rejectInvitation: rejectPlatformInvitation,
    transferOwnership: transferPlatformSurfaceOwnership,
    applyJoin: createImJoinRequest,
    approveJoin: approveImJoinRequest,
    rejectJoin: rejectImJoinRequest,
    cancelJoin: cancelImJoinRequest,
    leaveSurface: leaveImSurface,
    listSurfaceMembers: listPlatformSurfaceMembers,
    listJoinRequests: listPlatformJoinRequests,
    removeSurfaceMember: removePlatformSurfaceMember,
}

export function platformImWorkspaceReducer(
    state: PlatformImWorkspaceState,
    action: PlatformImWorkspaceAction,
): PlatformImWorkspaceState {
    switch (action.type) {
        case 'ACTIVITY_SELECTED':
            return {...state, activity: action.activity}
        case 'LOADING':
            return {...state, status: 'loading', error: undefined}
        case 'FAILED':
            return {...state, status: 'error', error: action.error}
        case 'BOOTSTRAPPED': {
            const selectedConversationId = retainSelection(
                state.selectedConversationId,
                action.bootstrap.conversations,
            )
            return {
                ...state,
                currentUser: action.bootstrap.currentUser,
                conversations: action.bootstrap.conversations,
                selectedConversationId,
                unreadTotal: action.bootstrap.unreadTotal,
                pendingFriendRequestCount: action.bootstrap.pendingFriendRequestCount,
                status: 'ready',
                error: undefined,
            }
        }
        case 'CONVERSATIONS_LOADED':
            return withUnreadTotal({
                ...state,
                conversations: action.conversations,
                selectedConversationId: retainSelection(
                    state.selectedConversationId,
                    action.conversations,
                ),
            })
        case 'CONVERSATION_SELECTED':
            return {
                ...state,
                activity: 'MESSAGES',
                selectedConversationId: action.conversationId,
            }
        case 'MESSAGES_MERGED':
            return withMessages(state, action.conversationId, action.messages)
        case 'MESSAGE_OPTIMISTIC':
            return withMessages(state, action.message.conversationId, [action.message])
        case 'MESSAGE_ACKNOWLEDGED':
            return withConversationMessage(withMessages(state, action.message.conversationId, [action.message]), action.message)
        case 'MESSAGE_FAILED':
            return updatePendingMessage(state, action.conversationId, action.clientMsgId, {
                status: 'FAILED',
                error: action.error,
            })
        case 'MESSAGE_RETRYING':
            return updatePendingMessage(state, action.conversationId, action.clientMsgId, {
                status: 'PENDING',
                error: undefined,
            })
        case 'UNREAD_UPDATED':
            return withUnreadTotal({
                ...state,
                conversations: state.conversations.map((conversation) => conversation.conversationId === action.unread.conversationId
                    ? {...conversation, unreadCount: action.unread.unreadCount}
                    : conversation),
            })
        case 'FRIENDS_LOADED':
            return {...state, friends: action.friends}
        case 'FRIEND_REQUESTS_LOADED':
            return action.box === 'INCOMING'
                ? {...state, incomingRequests: action.requests, pendingFriendRequestCount: pendingRequestCount(action.requests)}
                : {...state, outgoingRequests: action.requests}
        case 'USERS_LOADED':
            return {...state, userResults: action.users}
        case 'CHANNELS_LOADED':
            return {...state, channels: action.channels}
        case 'GROUPS_LOADED':
            return {...state, groups: action.groups}
        case 'INVITATIONS_LOADED':
            return {
                ...state,
                invitations: action.invitations,
                pendingInvitationCount: action.invitations.filter((item) => item.status === 'PENDING').length,
            }
        case 'SURFACE_MEMBERS_LOADED':
            return {...state, surfaceMembers: action.members}
        case 'JOIN_REQUESTS_LOADED':
            return {...state, joinRequests: action.requests}
    }
}

export function usePlatformImWorkspace(options: UsePlatformImWorkspaceOptions = {}) {
    const [state, dispatch] = useReducer(platformImWorkspaceReducer, initialState)
    const stateRef = useRef(state)
    const apiRef = useRef(options.api ?? defaultPlatformImWorkspaceApi)

    useEffect(() => {
        stateRef.current = state
    }, [state])

    useEffect(() => {
        apiRef.current = options.api ?? defaultPlatformImWorkspaceApi
    }, [options.api])

    const setFailure = useCallback((error: unknown) => {
        dispatch({type: 'FAILED', error: resolveErrorMessage(error)})
    }, [])

    const markConversationRead = useCallback(async (conversationId: number, messageSeq: number) => {
        if (messageSeq <= 0) {
            return
        }
        try {
            const unread = await apiRef.current.markRead(conversationId, {messageSeq})
            dispatch({type: 'UNREAD_UPDATED', unread})
        } catch (error) {
            setFailure(error)
        }
    }, [setFailure])

    const reconcileMessages = useCallback(async (conversationId: number, afterSeq?: number) => {
        const result = await apiRef.current.listMessages(conversationId, {
            page: 1,
            size: 50,
            afterSeq: afterSeq && afterSeq > 0 ? afterSeq : undefined,
        })
        dispatch({type: 'MESSAGES_MERGED', conversationId, messages: result.records})
        return result.records
    }, [])

    const refreshConversations = useCallback(async () => {
        const conversations = await apiRef.current.listConversations()
        dispatch({type: 'CONVERSATIONS_LOADED', conversations})
        return conversations
    }, [])

    const selectConversation = useCallback(async (conversationId: number) => {
        dispatch({type: 'CONVERSATION_SELECTED', conversationId})
        try {
            const messages = await reconcileMessages(conversationId)
            const conversation = stateRef.current.conversations.find((item) => item.conversationId === conversationId)
            const latestSeq = Math.max(conversation?.messageSeq ?? 0, maxMessageSeq(messages))
            await markConversationRead(conversationId, latestSeq)
        } catch (error) {
            setFailure(error)
        }
    }, [markConversationRead, reconcileMessages, setFailure])

    const reload = useCallback(async () => {
        dispatch({type: 'LOADING'})
        try {
            const [bootstrap, invitations] = await Promise.all([
                apiRef.current.getBootstrap(),
                apiRef.current.listInvitations(),
            ])
            dispatch({type: 'BOOTSTRAPPED', bootstrap})
            dispatch({type: 'INVITATIONS_LOADED', invitations: invitations.records})
            const selectedConversationId = retainSelection(
                stateRef.current.selectedConversationId,
                bootstrap.conversations,
            )
            if (selectedConversationId) {
                const messages = await reconcileMessages(selectedConversationId)
                await markConversationRead(
                    selectedConversationId,
                    Math.max(
                        bootstrap.conversations.find((item) => item.conversationId === selectedConversationId)?.messageSeq ?? 0,
                        maxMessageSeq(messages),
                    ),
                )
            }
        } catch (error) {
            setFailure(error)
        }
    }, [markConversationRead, reconcileMessages, setFailure])

    const handleResync = useCallback(async (payload: ImResyncPayload = {reason: 'CLIENT_RECONNECT'}) => {
        try {
            const conversations = await refreshConversations()
            const conversationId = stateRef.current.selectedConversationId ?? payload.conversationId ?? undefined
            if (!conversationId) {
                return
            }
            const currentMessages = stateRef.current.messagesByConversation[conversationId] ?? []
            const lastSeq = maxMessageSeq(currentMessages)
            const messages = await reconcileMessages(conversationId, lastSeq)
            const conversation = conversations.find((item) => item.conversationId === conversationId)
            await markConversationRead(
                conversationId,
                Math.max(conversation?.messageSeq ?? 0, payload.messageSeq ?? 0, maxMessageSeq(messages), lastSeq),
            )
        } catch (error) {
            setFailure(error)
        }
    }, [markConversationRead, reconcileMessages, refreshConversations, setFailure])

    const handleMessagePush = useCallback(async (payload: ImMessagePushPayload) => {
        if (payload.message) {
            dispatch({type: 'MESSAGE_ACKNOWLEDGED', message: payload.message})
        }
        if (payload.unread) {
            dispatch({type: 'UNREAD_UPDATED', unread: payload.unread})
        }
        try {
            const conversations = await refreshConversations()
            const conversationId = payload.message?.conversationId ?? payload.conversationId ?? undefined
            if (!conversationId || conversationId !== stateRef.current.selectedConversationId) {
                return
            }
            if (!payload.message) {
                const lastSeq = maxMessageSeq(stateRef.current.messagesByConversation[conversationId] ?? [])
                await reconcileMessages(conversationId, lastSeq)
            }
            const conversation = conversations.find((item) => item.conversationId === conversationId)
            await markConversationRead(
                conversationId,
                Math.max(conversation?.messageSeq ?? 0, payload.message?.messageSeq ?? payload.messageSeq ?? 0),
            )
        } catch (error) {
            setFailure(error)
        }
    }, [markConversationRead, reconcileMessages, refreshConversations, setFailure])

    const handleSendAck = useCallback((message: MessageView) => {
        dispatch({type: 'MESSAGE_ACKNOWLEDGED', message})
    }, [])

    const handleReadUpdate = useCallback((unread: UnreadView) => {
        dispatch({type: 'UNREAD_UPDATED', unread})
    }, [])

    const routeMessagePush = useCallback((payload: ImMessagePushPayload) => {
        void handleMessagePush(payload)
    }, [handleMessagePush])

    const routeResync = useCallback((payload: ImResyncPayload) => {
        void handleResync(payload)
    }, [handleResync])

    const socket = useImSocket({
        enabled: options.realtimeEnabled ?? true,
        activeConversationId: state.selectedConversationId,
        onMessagePush: routeMessagePush,
        onSendAck: handleSendAck,
        onReadUpdate: handleReadUpdate,
        onResync: routeResync,
        onError: setFailure,
    })

    const sendRequest = useCallback(async (request: SendMessageRequest) => {
        if (socket.sendMessage(request)) {
            return
        }
        try {
            const message = await apiRef.current.sendMessage(request)
            dispatch({type: 'MESSAGE_ACKNOWLEDGED', message})
        } catch (error) {
            dispatch({
                type: 'MESSAGE_FAILED',
                conversationId: request.conversationId,
                clientMsgId: request.clientMsgId,
                error: resolveErrorMessage(error),
            })
        }
    }, [socket])

    const sendText = useCallback(async (content: string) => {
        const conversation = stateRef.current.conversations.find(
            (item) => item.conversationId === stateRef.current.selectedConversationId,
        )
        const normalized = content.trim()
        if (!conversation?.canPost || !normalized) {
            return false
        }
        const clientMsgId = createClientMessageId()
        const optimistic = optimisticMessage(conversation, stateRef.current.currentUser, clientMsgId, normalized)
        dispatch({type: 'MESSAGE_OPTIMISTIC', message: optimistic})
        await sendRequest(messageRequest(optimistic))
        return true
    }, [sendRequest])

    const retryMessage = useCallback(async (conversationId: number, clientMsgId: string) => {
        const message = (stateRef.current.messagesByConversation[conversationId] ?? [])
            .find((item) => item.clientMsgId === clientMsgId)
        if (!message || !message.content) {
            return false
        }
        dispatch({type: 'MESSAGE_RETRYING', conversationId, clientMsgId})
        await sendRequest(messageRequest(message))
        return true
    }, [sendRequest])

    const refreshFriends = useCallback(async () => {
        try {
            const [friends, incoming, outgoing] = await Promise.all([
                apiRef.current.listFriends(),
                apiRef.current.listFriendRequests('INCOMING'),
                apiRef.current.listFriendRequests('OUTGOING'),
            ])
            dispatch({type: 'FRIENDS_LOADED', friends: friends.records})
            dispatch({type: 'FRIEND_REQUESTS_LOADED', box: 'INCOMING', requests: incoming.records})
            dispatch({type: 'FRIEND_REQUESTS_LOADED', box: 'OUTGOING', requests: outgoing.records})
        } catch (error) {
            setFailure(error)
        }
    }, [setFailure])

    const searchUsers = useCallback(async (keyword: string) => {
        try {
            const result = keyword.trim() ? await apiRef.current.searchUsers(keyword.trim()) : emptyPage<PlatformUserView>()
            dispatch({type: 'USERS_LOADED', users: result.records})
        } catch (error) {
            setFailure(error)
        }
    }, [setFailure])

    const refreshGroups = useCallback(async () => {
        try {
            const groups = await apiRef.current.listGroups()
            dispatch({type: 'GROUPS_LOADED', groups})
        } catch (error) {
            setFailure(error)
        }
    }, [setFailure])

    const refreshChannels = useCallback(async () => {
        try {
            const channels = await apiRef.current.listChannels()
            dispatch({type: 'CHANNELS_LOADED', channels})
            return channels
        } catch (error) {
            setFailure(error)
            return []
        }
    }, [setFailure])

    const refreshInvitations = useCallback(async () => {
        try {
            const invitations = await apiRef.current.listInvitations()
            dispatch({type: 'INVITATIONS_LOADED', invitations: invitations.records})
            return invitations.records
        } catch (error) {
            setFailure(error)
            return []
        }
    }, [setFailure])

    const listChannelGroups = useCallback(
        (channelId: number) => apiRef.current.listChannelGroups(channelId),
        [],
    )

    const refreshSurfaceAdmin = useCallback(async (surfaceType: ImSurfaceType, surfaceId: number) => {
        try {
            const [members, requests] = await Promise.all([
                apiRef.current.listSurfaceMembers(surfaceType, surfaceId),
                apiRef.current.listJoinRequests(surfaceType, surfaceId),
            ])
            dispatch({type: 'SURFACE_MEMBERS_LOADED', members: members.records})
            dispatch({type: 'JOIN_REQUESTS_LOADED', requests: requests.records})
        } catch (error) {
            setFailure(error)
        }
    }, [setFailure])

    const refreshPlatformData = useCallback(async () => {
        await Promise.all([refreshConversations(), refreshFriends(), refreshChannels(), refreshGroups(), refreshInvitations()])
    }, [refreshChannels, refreshConversations, refreshFriends, refreshGroups, refreshInvitations])

    useEffect(() => {
        void reload()
    }, [reload])

    const selectedConversation = useMemo(
        () => state.conversations.find((item) => item.conversationId === state.selectedConversationId),
        [state.conversations, state.selectedConversationId],
    )

    return {
        ...state,
        selectedConversation,
        messages: state.selectedConversationId
            ? state.messagesByConversation[state.selectedConversationId] ?? []
            : [],
        selectActivity: (activity: PlatformImActivity) => dispatch({type: 'ACTIVITY_SELECTED', activity}),
        selectConversation,
        reload,
        refreshConversations,
        refreshFriends,
        refreshChannels,
        refreshGroups,
        refreshInvitations,
        refreshSurfaceAdmin,
        refreshPlatformData,
        searchUsers,
        sendText,
        retryMessage,
        handleResync,
        requestFriend: async (request: PlatformFriendRequestCreateRequest) => {
            await apiRef.current.createFriendRequest(request)
            await refreshFriends()
        },
        acceptFriendRequest: async (id: number, request?: PlatformFriendDecisionRequest) => {
            await apiRef.current.acceptFriendRequest(id, request)
            await refreshPlatformData()
        },
        rejectFriendRequest: async (id: number, request?: PlatformFriendDecisionRequest) => {
            await apiRef.current.rejectFriendRequest(id, request)
            await refreshFriends()
        },
        cancelFriendRequest: async (id: number) => {
            await apiRef.current.cancelFriendRequest(id)
            await refreshFriends()
        },
        updateFriendRemark: async (friendUserId: number, request: PlatformFriendRemarkRequest) => {
            await apiRef.current.updateFriendRemark(friendUserId, request)
            await refreshFriends()
        },
        removeFriend: async (friendUserId: number) => {
            await apiRef.current.removeFriend(friendUserId)
            await refreshPlatformData()
        },
        openDm: async (targetUserId: number) => {
            const conversation = await apiRef.current.openDm({targetUserId})
            await refreshConversations()
            await selectConversation(conversation.id)
        },
        createChannel: async (request: PlatformSurfaceCreateRequest) => {
            const channel = await apiRef.current.createChannel(request)
            await Promise.all([refreshChannels(), refreshConversations()])
            return channel
        },
        createGroup: async (request: PlatformSurfaceCreateRequest) => {
            const group = await apiRef.current.createGroup(request)
            await refreshPlatformData()
            return group
        },
        createChannelGroup: async (channelId: number, request: PlatformChannelGroupCreateRequest) => {
            const group = await apiRef.current.createChannelGroup(channelId, request)
            await refreshConversations()
            return group
        },
        listChannelGroups,
        inviteSurface: (
            surfaceType: ImSurfaceType,
            surfaceId: number,
            request: PlatformSurfaceInvitationRequest,
        ) => apiRef.current.inviteSurface(surfaceType, surfaceId, request),
        acceptInvitation: async (id: number) => {
            await apiRef.current.acceptInvitation(id)
            await refreshPlatformData()
        },
        rejectInvitation: async (id: number) => {
            await apiRef.current.rejectInvitation(id)
            await refreshInvitations()
        },
        transferOwnership: async (
            surfaceType: ImSurfaceType,
            surfaceId: number,
            request: PlatformOwnershipTransferRequest,
        ) => {
            await apiRef.current.transferOwnership(surfaceType, surfaceId, request)
            await Promise.all([refreshChannels(), refreshGroups(), refreshConversations()])
        },
        applyJoin: async (request: JoinRequestCreateRequest) => {
            const result = await apiRef.current.applyJoin(request)
            await refreshPlatformData()
            return result
        },
        approveJoin: async (surfaceType: ImSurfaceType, surfaceId: number, id: number) => {
            await apiRef.current.approveJoin(id)
            await Promise.all([
                refreshSurfaceAdmin(surfaceType, surfaceId),
                refreshConversations(),
                refreshGroups(),
            ])
        },
        rejectJoin: async (
            surfaceType: ImSurfaceType,
            surfaceId: number,
            id: number,
            request?: JoinRequestRejectRequest,
        ) => {
            await apiRef.current.rejectJoin(id, request)
            await refreshSurfaceAdmin(surfaceType, surfaceId)
        },
        cancelJoin: async (id: number) => {
            const result = await apiRef.current.cancelJoin(id)
            await refreshGroups()
            return result
        },
        leaveSurface: async (surfaceType: ImSurfaceType, surfaceId: number) => {
            await apiRef.current.leaveSurface(surfaceType, surfaceId)
            await refreshPlatformData()
        },
        removeSurfaceMember: async (surfaceType: ImSurfaceType, surfaceId: number, userId: number) => {
            await apiRef.current.removeSurfaceMember(surfaceType, surfaceId, userId)
            await Promise.all([
                refreshSurfaceAdmin(surfaceType, surfaceId),
                refreshConversations(),
                refreshGroups(),
            ])
        },
    }
}

function withMessages(
    state: PlatformImWorkspaceState,
    conversationId: number,
    incoming: OptimisticMessageView[],
): PlatformImWorkspaceState {
    return {
        ...state,
        messagesByConversation: {
            ...state.messagesByConversation,
            [conversationId]: mergeMessages(state.messagesByConversation[conversationId] ?? [], incoming),
        },
    }
}

export function mergeMessages(
    current: OptimisticMessageView[],
    incoming: OptimisticMessageView[],
): OptimisticMessageView[] {
    const merged = [...current]
    incoming.forEach((message) => {
        const index = merged.findIndex((item) => item.id === message.id
            || (message.clientMsgId && item.clientMsgId === message.clientMsgId)
            || (!item.optimistic && !message.optimistic && item.messageSeq === message.messageSeq))
        if (index >= 0) {
            merged[index] = {...merged[index], ...message, optimistic: message.optimistic ?? false, error: message.error}
        } else {
            merged.push(message)
        }
    })
    return merged.sort((left, right) => left.messageSeq - right.messageSeq || left.id - right.id)
}

function withConversationMessage(state: PlatformImWorkspaceState, message: MessageView) {
    return {
        ...state,
        conversations: state.conversations.map((conversation) => conversation.conversationId === message.conversationId
            ? {
                ...conversation,
                messageSeq: Math.max(conversation.messageSeq, message.messageSeq),
                lastMessageId: message.id,
                lastMessageAt: message.sentAt,
                lastMessage: message,
                lastActiveAt: message.sentAt,
            }
            : conversation),
    }
}

function updatePendingMessage(
    state: PlatformImWorkspaceState,
    conversationId: number,
    clientMsgId: string,
    update: Partial<OptimisticMessageView>,
) {
    return {
        ...state,
        messagesByConversation: {
            ...state.messagesByConversation,
            [conversationId]: (state.messagesByConversation[conversationId] ?? []).map((message) => (
                message.clientMsgId === clientMsgId ? {...message, ...update} : message
            )),
        },
    }
}

function withUnreadTotal(state: PlatformImWorkspaceState) {
    return {
        ...state,
        unreadTotal: state.conversations.reduce((total, conversation) => total + conversation.unreadCount, 0),
    }
}

function retainSelection(
    current: number | undefined,
    conversations: PlatformConversationView[],
    fallback?: number,
) {
    if (current && conversations.some((conversation) => conversation.conversationId === current)) {
        return current
    }
    return fallback ?? conversations[0]?.conversationId
}

function pendingRequestCount(requests: PlatformFriendRequestView[]) {
    return requests.filter((request) => request.status === 'PENDING').length
}

function maxMessageSeq(messages: Array<Pick<MessageView, 'messageSeq'>>) {
    return messages.reduce((maximum, message) => Math.max(maximum, message.messageSeq), 0)
}

function optimisticMessage(
    conversation: PlatformConversationView,
    currentUser: PlatformUserView | undefined,
    clientMsgId: string,
    content: string,
): OptimisticMessageView {
    return {
        id: -(Date.now() * 1000 + ++optimisticMessageSequence),
        conversationId: conversation.conversationId,
        senderUserId: currentUser?.userId,
        messageSeq: conversation.messageSeq + 1,
        clientMsgId,
        messageType: 'TEXT',
        content,
        status: 'PENDING',
        sentAt: new Date().toISOString(),
        optimistic: true,
    }
}

function messageRequest(message: OptimisticMessageView): SendMessageRequest {
    return {
        conversationId: message.conversationId,
        clientMsgId: message.clientMsgId ?? createClientMessageId(),
        messageType: message.messageType,
        content: message.content,
        payloadJson: message.payloadJson,
        metadataJson: message.metadataJson,
    }
}

function createClientMessageId() {
    return typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : `client-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function emptyPage<T>(): PageResult<T> {
    return {records: [], page: 1, size: 20, total: 0, totalPages: 0}
}
