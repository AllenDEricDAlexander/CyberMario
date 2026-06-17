import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as ReplayPage} from './ReplayPage'

vi.mock('react-router', () => ({
    useParams: () => ({roomId: '7'}),
}))

vi.mock('./clocktowerService', () => ({
    getClocktowerReplay: vi.fn().mockResolvedValue({roomId: 7, mode: 'PUBLIC', events: []}),
    getClocktowerReplayVotes: vi.fn().mockResolvedValue([]),
}))

describe('ReplayPage', () => {
    test('renders replay timeline and vote review sections', () => {
        const markup = renderToStaticMarkup(<ReplayPage/>)

        expect(markup).toContain('钟楼回放')
        expect(markup).toContain('事件时间线')
        expect(markup).toContain('投票复盘')
        expect(markup).toContain('使用死票')
    })
})
