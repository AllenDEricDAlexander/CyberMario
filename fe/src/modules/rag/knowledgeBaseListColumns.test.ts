import {describe, expect, test} from 'vitest'
import {knowledgeBaseTableColumns, knowledgeBaseTableScrollX} from './KnowledgeBaseListPage'

describe('knowledge base list table layout', () => {
    test('keeps the description column readable beside fixed operation columns', () => {
        const columns = knowledgeBaseTableColumns({
            canEdit: true,
            canGrantUsers: true,
            canDelete: true,
            onEdit: () => undefined,
            onGrantUsers: () => undefined,
            onDelete: () => undefined,
        })
        const descriptionColumn = columns.find((column) => 'dataIndex' in column && column.dataIndex === 'description')

        expect(descriptionColumn).toMatchObject({
            width: 260,
            ellipsis: true,
        })
        expect(knowledgeBaseTableScrollX).toBeGreaterThanOrEqual(1300)
    })
})
