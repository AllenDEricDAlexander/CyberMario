/// <reference types="node" />

import {readFileSync, readdirSync} from 'node:fs'
import {resolve} from 'node:path'
import {describe, expect, test} from 'vitest'

const stylesDir = resolve(process.cwd(), 'src/styles')

/**
 * `global.css` only imports the layers, so assertions run against every stylesheet
 * concatenated — that keeps the test stable when rules move between layers.
 */
const css = readdirSync(stylesDir)
    .filter((file) => file.endsWith('.css'))
    .map((file) => readFileSync(resolve(stylesDir, file), 'utf8'))
    .join('\n')

function cssRule(selector: string) {
    const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const match = css.match(new RegExp(`(^|\\n)${escapedSelector}\\s*\\{([^}]*)\\}`))
    return match?.[2] ?? ''
}

describe('style layers', () => {
    test('global.css wires every layer in cascade order', () => {
        const entry = readFileSync(resolve(stylesDir, 'global.css'), 'utf8')
        const imports = [...entry.matchAll(/@import '\.\/(\w+)\.css'/g)].map((match) => match[1])
        expect(imports).toEqual(['tokens', 'base', 'layout', 'components', 'chat'])
    })

    test('defines light and dark token sets', () => {
        expect(css).toContain(":root[data-theme='dark']")
        expect(cssRule(":root[data-theme='dark']")).toContain('color-scheme: dark')
        expect(css).toContain('--space-md: 16px')
        expect(css).toContain('--header-height: 60px')
    })
})

describe('admin layout styles', () => {
    test('isolates sider and content scrolling', () => {
        expect(cssRule('body')).toContain('overflow: hidden')
        expect(cssRule('.admin-sider')).toContain('height: 100svh')
        expect(cssRule('.admin-sider .ant-layout-sider-children')).toContain('min-height: 0')
        expect(cssRule('.admin-sider .ant-menu')).toContain('overflow-y: auto')
        expect(cssRule('.admin-content')).toContain('height: calc(100svh - var(--header-height))')
        expect(cssRule('.admin-content')).toContain('overflow-y: auto')
    })

    test('keeps the header pinned above scrolling content', () => {
        expect(cssRule('.admin-header')).toContain('position: sticky')
        expect(cssRule('.admin-header')).toContain('height: var(--header-height)')
    })
})

describe('auth screen styles', () => {
    test('keeps the auth error alert readable on the dark glass panel', () => {
        expect(cssRule('.auth-alert')).toContain('background: rgba(70, 21, 36, 0.92)')
        expect(cssRule('.auth-alert')).toContain('color: #ffeef4 !important')
        expect(css).toContain('.auth-alert .ant-alert-message,')
        expect(css).toContain('color: #ffeef4 !important')
        expect(css).toContain('.auth-alert .ant-alert-icon')
        expect(css).toContain('color: #ff6b94 !important')
    })

    test('keeps auth inputs dark when Ant Design applies active and error states', () => {
        expect(css).toContain('.auth-card .ant-input-status-error,')
        expect(css).toContain('.auth-card .ant-input-affix-wrapper-status-error,')
        expect(css).toContain('.auth-card .ant-input:focus,')
        expect(css).toContain('.auth-card .ant-input-affix-wrapper-focused')
        expect(css).toContain('background: rgba(3, 15, 16, 0.36) !important')
        expect(css).toContain('.auth-card .ant-input:-webkit-autofill')
        expect(css).toContain('-webkit-box-shadow: 0 0 0 1000px rgba(3, 15, 16, 0.36) inset')
    })
})
