import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {InvestmentDecimalText} from './InvestmentDecimalText'

describe('InvestmentDecimalText', () => {
    test('preserves arbitrary precision server strings without number conversion', () => {
        const decimal = '12345678901234567890.123456789012345678'
        const markup = renderToStaticMarkup(<InvestmentDecimalText suffix="USDT" value={decimal}/>)

        expect(markup).toContain(`${decimal} USDT`)
    })

    test('renders an explicit empty placeholder', () => {
        expect(renderToStaticMarkup(<InvestmentDecimalText value={null}/>)).toContain('-')
    })
})
