import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {MoneyText} from './MoneyText'

describe('MoneyText', () => {
    test('MoneyText renders CNY values with two fraction digits', () => {
        const markup = renderToStaticMarkup(<MoneyText value={12.3}/>)

        expect(markup).toContain('¥12.30')
    })
})
