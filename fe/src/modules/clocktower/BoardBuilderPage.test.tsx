import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {Component as BoardBuilderPage, savedBoardColumns} from './BoardBuilderPage'
import {Table} from 'antd'

vi.mock('./clocktowerService', () => ({
    getClocktowerScripts: vi.fn().mockResolvedValue([
        {scriptCode: 'TROUBLE_BREWING', name: '暗流涌动', edition: 'BASE_3', minPlayers: 5, maxPlayers: 15, roleCount: 22, enabled: true},
    ]),
    getClocktowerRoles: vi.fn().mockResolvedValue([]),
    generateClocktowerBoard: vi.fn(),
    validateClocktowerBoard: vi.fn(),
    saveClocktowerBoard: vi.fn(),
    listClocktowerBoards: vi.fn().mockResolvedValue({records: [], page: 1, size: 10, total: 0, totalPages: 0}),
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
        expect(markup).toContain('保存当前配板')
        expect(markup).toContain('校验结果')
        expect(markup).toContain('我的配板库')
    })

    test('renders saved board localized role names with official codes', () => {
        const markup = renderToStaticMarkup(
            <Table
                columns={savedBoardColumns(vi.fn())}
                dataSource={[
                    {
                        boardId: 1,
                        boardCode: 'board-1',
                        scriptCode: 'TROUBLE_BREWING',
                        playerCount: 5,
                        roleCodes: ['CHEF'],
                        roles: [{roleCode: 'CHEF', roleName: '厨师', roleType: {code: 1, desc: '镇民'}}],
                        validation: {valid: true, roleTypeCounts: {}, violations: [], scores: []},
                        valid: true,
                        createdAt: '2026-06-19T02:30:00Z',
                    },
                ]}
                pagination={false}
                rowKey="boardId"
            />,
        )

        expect(markup).toContain('厨师')
        expect(markup).toContain('CHEF')
    })
})
