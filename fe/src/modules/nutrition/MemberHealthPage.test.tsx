import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as MemberHealthPage} from './MemberHealthPage'

describe('MemberHealthPage', () => {
    test('member health page lists family health profiles with read-only guardian preview', () => {
        const markup = renderToStaticMarkup(<MemberHealthPage/>)

        expect(markup).toContain('成员健康')
        expect(markup).toContain('成员档案')
        expect(markup).toContain('健康档案')
        expect(markup).toContain('监护关系预览')
        expect(markup).toContain('查看监护预览')
        expect(markup).not.toContain('监护人控制')
        expect(markup).toContain('目标热量')
    })
})
