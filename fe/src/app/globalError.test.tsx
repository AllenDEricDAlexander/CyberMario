/// <reference types="node" />

import {readFileSync} from 'node:fs'
import {resolve} from 'node:path'
import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test, vi} from 'vitest'
import {
    clearGlobalError,
    GlobalErrorAlert,
    registerGlobalErrorHandler,
    reportGlobalError,
} from './globalError'

describe('global error reporting', () => {
    test('renders a closable error alert with the reported message', () => {
        const markup = renderToStaticMarkup(
            <GlobalErrorAlert message="Network Error" onClose={vi.fn()}/>,
        )

        expect(markup).toContain('Network Error')
        expect(markup).toContain('ant-alert-error')
        expect(markup).toContain('ant-alert-close-icon')
        expect(markup).toContain('global-error-alert-popup')
    })

    test('pins the banner to the top centre of the viewport', () => {
        // Positioning moved from inline styles into the themed stylesheet.
        const css = readFileSync(resolve(process.cwd(), 'src/styles/components.css'), 'utf8')
        const rule = css.match(/\.global-error-alert-popup\s*\{([^}]*)\}/)?.[1] ?? ''

        expect(rule).toContain('position: fixed')
        expect(rule).toContain('left: 50%')
        expect(rule).toContain('transform: translateX(-50%)')
        expect(rule).toContain('width: max-content')
        expect(rule).toContain('max-width: calc(100vw - 32px)')
    })

    test('reports and clears resolved error messages', () => {
        const handler = vi.fn()
        const dispose = registerGlobalErrorHandler(handler)

        reportGlobalError(new Error('Network Error'))
        expect(handler).toHaveBeenCalledWith('Network Error')

        clearGlobalError()
        expect(handler).toHaveBeenLastCalledWith(undefined)

        dispose()
        reportGlobalError(new Error('Ignored'))
        expect(handler).toHaveBeenCalledTimes(2)
    })
})
