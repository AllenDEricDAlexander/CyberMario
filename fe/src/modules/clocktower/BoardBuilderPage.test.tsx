import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as BoardBuilderPage} from './BoardBuilderPage'

vi.mock('./clocktowerService', () => ({
    getClocktowerScripts: vi.fn().mockResolvedValue([
        {scriptCode: 'TROUBLE_BREWING', name: '暗流涌动', edition: 'BASE_3', minPlayers: 5, maxPlayers: 15, roleCount: 22, enabled: true},
    ]),
    generateClocktowerBoard: vi.fn(),
    validateClocktowerBoard: vi.fn(),
    saveClocktowerBoard: vi.fn(),
    listClocktowerBoards: vi.fn().mockResolvedValue([]),
    deleteClocktowerBoard: vi.fn(),
}))

describe('BoardBuilderPage', () => {
    test('renders script and board controls', () => {
        const markup = renderToStaticMarkup(<BoardBuilderPage/>)

        expect(markup).toContain('钟楼配板')
        expect(markup).toContain('剧本')
        expect(markup).toContain('人数')
        expect(markup).toContain('生成配板')
        expect(markup).toContain('手动校验')
        expect(markup).toContain('已保存配板')
    })
})
