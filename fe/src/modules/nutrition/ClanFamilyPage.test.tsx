import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as ClanFamilyPage} from './ClanFamilyPage'

describe('ClanFamilyPage', () => {
    test('clan family page exposes read-only grant preview controls', () => {
        const markup = renderToStaticMarkup(<ClanFamilyPage/>)

        expect(markup).toContain('家庭营养')
        expect(markup).toContain('Clan 列表')
        expect(markup).toContain('家庭列表')
        expect(markup).toContain('关联关系')
        expect(markup).toContain('授权只读预览')
        expect(markup).toContain('查看授权预览')
        expect(markup).not.toContain('打开授权')
    })
})
