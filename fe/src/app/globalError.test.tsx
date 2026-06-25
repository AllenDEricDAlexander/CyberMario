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
        expect(markup).toContain('position:fixed')
        expect(markup).toContain('left:50%')
        expect(markup).toContain('transform:translateX(-50%)')
        expect(markup).toContain('width:max-content')
        expect(markup).toContain('max-width:calc(100vw - 32px)')
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
