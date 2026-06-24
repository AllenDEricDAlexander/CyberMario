import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as ReplayListPage} from './ReplayListPage'

vi.mock('react-router', () => ({
    useNavigate: () => vi.fn(),
}))

vi.mock('./clocktowerService', () => ({
    listClocktowerGameHistory: vi.fn().mockResolvedValue([]),
    listClocktowerRooms: vi.fn().mockResolvedValue([]),
}))

describe('ReplayListPage', () => {
    test('renders game history replay entry surface', () => {
        const markup = renderToStaticMarkup(<ReplayListPage/>)

        expect(markup).toContain('钟楼回放复盘')
        expect(markup).toContain('游戏编号')
        expect(markup).toContain('房间 / 游戏')
        expect(markup).toContain('身份 / 权限')
        expect(markup).toContain('查看回放')
    })
})
