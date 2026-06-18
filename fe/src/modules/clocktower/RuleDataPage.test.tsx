import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as RuleDataPage} from './RuleDataPage'

vi.mock('./clocktowerService', () => ({
    getClocktowerScripts: vi.fn().mockResolvedValue([
        {
            scriptCode: 'TROUBLE_BREWING',
            name: '暗流涌动',
            edition: 'BASE_3',
            minPlayers: 5,
            maxPlayers: 15,
            roleCount: 22,
            enabled: true,
        },
    ]),
    getClocktowerRoles: vi.fn().mockResolvedValue([]),
    getClocktowerGroupedNightOrder: vi.fn().mockResolvedValue({firstNight: [], otherNight: []}),
    getClocktowerTerms: vi.fn().mockResolvedValue([]),
    getClocktowerJinxRules: vi.fn().mockResolvedValue([]),
}))

describe('RuleDataPage', () => {
    test('renders structured rule data controls', () => {
        const markup = renderToStaticMarkup(<RuleDataPage/>)

        expect(markup).toContain('钟楼规则')
        expect(markup).toContain('剧本规则')
        expect(markup).toContain('术语')
        expect(markup).toContain('相克规则')
        expect(markup).toContain('关键词')
        expect(markup).toContain('分类')
        expect(markup).toContain('角色代码')
        expect(markup).toContain('严重级别')
        expect(markup).toContain('查询')
        expect(markup).toContain('首夜')
        expect(markup).toContain('其他夜晚')
    })
})
