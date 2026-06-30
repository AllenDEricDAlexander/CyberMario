import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {RiskTag} from './RiskTag'

describe('RiskTag', () => {
    test('RiskTag renders blocking high risk as error', () => {
        const markup = renderToStaticMarkup(<RiskTag blocking value="HIGH"/>)

        expect(markup).toContain('高风险')
        expect(markup).toContain('ant-tag-error')
    })
})
