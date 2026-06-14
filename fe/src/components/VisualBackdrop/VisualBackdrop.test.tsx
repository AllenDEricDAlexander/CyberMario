import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {VisualBackdrop} from './index'

describe('VisualBackdrop', () => {
    test('renders grid, glow, and optional particle layers', () => {
        const markup = renderToStaticMarkup(<VisualBackdrop particleCount={6} variant="auth"/>)

        expect(markup).toContain('visual-backdrop auth')
        expect(markup).toContain('visual-backdrop-grid')
        expect(markup).toContain('visual-backdrop-glow primary')
        expect(markup).toContain('visual-backdrop-particles')
        expect(markup.match(/class="visual-backdrop-particle"/g)).toHaveLength(6)
    })

    test('omits the particle layer when the particle count is zero', () => {
        const markup = renderToStaticMarkup(<VisualBackdrop particleCount={0} variant="content"/>)

        expect(markup).toContain('visual-backdrop content')
        expect(markup).not.toContain('visual-backdrop-particles')
    })
})
