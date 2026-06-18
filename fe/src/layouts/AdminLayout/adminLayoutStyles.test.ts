/// <reference types="node" />

import {readFileSync} from 'node:fs'
import {describe, expect, test} from 'vitest'

const css = readFileSync(new URL('../../styles/global.css', import.meta.url), 'utf8')

function cssRule(selector: string) {
    const escapedSelector = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const match = css.match(new RegExp(`${escapedSelector}\\s*\\{([^}]*)\\}`))
    return match?.[1] ?? ''
}

describe('admin layout styles', () => {
    test('isolates sider and content scrolling', () => {
        expect(cssRule('body')).toContain('overflow: hidden')
        expect(cssRule('.admin-sider')).toContain('height: 100svh')
        expect(cssRule('.admin-sider .ant-layout-sider-children')).toContain('min-height: 0')
        expect(cssRule('.admin-sider .ant-menu')).toContain('overflow-y: auto')
        expect(cssRule('.admin-content')).toContain('height: calc(100svh - 60px)')
        expect(cssRule('.admin-content')).toContain('overflow-y: auto')
    })

    test('keeps auth error alert readable on the dark glass panel', () => {
        expect(cssRule('.auth-alert')).toContain('background: rgba(70, 21, 36, 0.92)')
        expect(cssRule('.auth-alert')).toContain('color: #ffeef4 !important')
        expect(css).toContain('.auth-alert .ant-alert-message,')
        expect(css).toContain('color: #ffeef4 !important')
        expect(css).toContain('.auth-alert .ant-alert-icon')
        expect(css).toContain('color: #ff6b94 !important')
    })
})
