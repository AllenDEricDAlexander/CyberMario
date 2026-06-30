import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {ImportJobPanel} from './ImportJobPanel'
import type {NutritionImportJobResponse} from '../nutritionTypes'

const job: NutritionImportJobResponse = {
    id: 12,
    familyId: null,
    importType: 'STANDARD_FOOD',
    fileName: 'foods.csv',
    status: 'PREVIEW_READY',
    totalRows: 10,
    successRows: 8,
    failedRows: 2,
    warningRows: 1,
    errorSummary: '2 rows failed',
    errors: [
        {
            id: 1,
            rowNo: 4,
            columnName: 'calories',
            errorCode: 'INVALID_NUMBER',
            errorMessage: '热量必须是数字',
            severity: 'ERROR',
        },
    ],
    createdAt: '2026-07-01T00:00:00Z',
    completedAt: null,
    confirmedAt: null,
}

describe('ImportJobPanel', () => {
    test('ImportJobPanel renders failed row counts and confirm action', () => {
        const markup = renderToStaticMarkup(<ImportJobPanel job={job} onConfirm={vi.fn()}/>)

        expect(markup).toContain('失败行')
        expect(markup).toContain('2')
        expect(markup).toContain('确认导入')
        expect(markup).toContain('热量必须是数字')
    })
})
