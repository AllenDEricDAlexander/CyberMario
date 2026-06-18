import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {BoardCandidateTable} from './components/BoardCandidateTable'

describe('BoardCandidateTable', () => {
    test('renders localized role names with official codes from role summaries', () => {
        const markup = renderToStaticMarkup(
            <BoardCandidateTable
                candidates={[
                    {
                        candidateId: 'candidate-1',
                        scriptCode: 'TROUBLE_BREWING',
                        playerCount: 5,
                        roleCodes: ['CHEF'],
                        roles: [{roleCode: 'CHEF', roleName: '厨师', roleType: {code: 1, desc: '镇民'}}],
                        validation: {valid: true, roleTypeCounts: {}, violations: [], scores: []},
                        scores: [],
                    },
                ]}
                onSave={vi.fn()}
            />,
        )

        expect(markup).toContain('厨师')
        expect(markup).toContain('CHEF')
    })

    test('falls back to role codes when summaries are missing', () => {
        const markup = renderToStaticMarkup(
            <BoardCandidateTable
                candidates={[
                    {
                        candidateId: 'candidate-1',
                        scriptCode: 'TROUBLE_BREWING',
                        playerCount: 5,
                        roleCodes: ['IMP'],
                        validation: {valid: true, roleTypeCounts: {}, violations: [], scores: []},
                        scores: [],
                    },
                ]}
            />,
        )

        expect(markup).toContain('IMP')
    })
})
