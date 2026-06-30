import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {Component as ClanFamilyPage} from './ClanFamilyPage'

describe('ClanFamilyPage', () => {
    test('clan family page exposes explicit grant controls', () => {
        const markup = renderToStaticMarkup(<ClanFamilyPage/>)

        expect(markup).toContain('家庭营养')
        expect(markup).toContain('Clan 列表')
        expect(markup).toContain('家庭列表')
        expect(markup).toContain('关联关系')
        expect(markup).toContain('显式授权')
        expect(markup).toContain('打开授权')
    })
})
