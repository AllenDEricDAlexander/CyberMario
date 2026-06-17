import {renderToStaticMarkup} from 'react-dom/server'
import {beforeEach, describe, expect, test, vi} from 'vitest'
import {Component as ReplayPage} from './ReplayPage'

type MockAuthState = {
    roleCodes: string[]
    adminBypass: boolean
}

const authStateKey = '__clocktowerReplayAuthState__'
const authState = ((globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey] = {
    roleCodes: ['CLOCKTOWER_STORYTELLER'],
    adminBypass: false,
})

vi.mock('react-router', () => ({
    useParams: () => ({roomId: '7'}),
}))

vi.mock('../auth/authStore', () => ({
    useAuth: () => ({
        roleCodes: (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].roleCodes,
        hasPermission: () => (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].adminBypass,
    }),
    hasAdminPermissionBypass: () => (globalThis as typeof globalThis & Record<string, MockAuthState>)[authStateKey].adminBypass,
}))

vi.mock('./clocktowerService', () => ({
    getClocktowerReplay: vi.fn().mockResolvedValue({roomId: 7, mode: 'PUBLIC', events: []}),
    getClocktowerReplayVotes: vi.fn().mockResolvedValue([]),
}))

describe('ReplayPage', () => {
    beforeEach(() => {
        authState.roleCodes = ['CLOCKTOWER_STORYTELLER']
        authState.adminBypass = false
    })

    test('renders replay timeline and vote review sections', () => {
        const markup = renderToStaticMarkup(<ReplayPage/>)

        expect(markup).toContain('钟楼回放')
        expect(markup).toContain('事件时间线')
        expect(markup).toContain('投票复盘')
        expect(markup).toContain('使用死票')
    })

    test('does not render vote review section for player role', () => {
        authState.roleCodes = ['CLOCKTOWER_PLAYER']
        const markup = renderToStaticMarkup(<ReplayPage/>)

        expect(markup).toContain('钟楼回放')
        expect(markup).toContain('事件时间线')
        expect(markup).not.toContain('投票复盘')
        expect(markup).not.toContain('使用死票')
    })
})
