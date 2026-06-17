import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as ReplayListPage} from './ReplayListPage'

vi.mock('react-router', () => ({
    useNavigate: () => vi.fn(),
}))

vi.mock('./clocktowerService', () => ({
    listClocktowerRooms: vi.fn().mockResolvedValue([]),
}))

describe('ReplayListPage', () => {
    test('renders replay review entry surface', () => {
        const markup = renderToStaticMarkup(<ReplayListPage/>)

        expect(markup).toContain('钟楼回放复盘')
        expect(markup).toContain('房间')
        expect(markup).toContain('查看回放')
    })
})
