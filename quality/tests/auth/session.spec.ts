import {test, expect} from '../../fixtures/auth.fixture'
import {readApiEnvelope} from '../../models/api'
import {AdminShell} from '../../pages/AdminShell'
import {LoginPage} from '../../pages/LoginPage'

test.describe('browser session', () => {
    test('restores the authenticated user after reload and protected navigation',
        async ({page, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('session-restore')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            expect((await readApiEnvelope(
                await loginPage.login(identity.accountNo, identity.password))).code).toBe('0')

            const meResponse = page.waitForResponse((response) =>
                response.request().method() === 'GET'
                && new URL(response.url()).pathname === '/api/auth/me')
            await page.reload()
            expect((await readApiEnvelope(await meResponse)).code).toBe('0')
            await expect(page.getByRole('button', {name: identity.nickname})).toBeVisible()

            await page.goto('/account/settings')
            await expect(page).toHaveURL(/\/account\/settings$/)
            await expect(page.getByText('维护当前账号的基础资料和登录密码。')).toBeVisible()
        })

    test('clears cookies and protects routes after logout',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('logout')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            expect((await readApiEnvelope(
                await loginPage.login(identity.accountNo, identity.password))).code).toBe('0')

            const shell = new AdminShell(page)
            const logoutResponse = await shell.logout(identity.nickname)
            expect((await readApiEnvelope(logoutResponse)).code).toBe('0')
            await expect(page).toHaveURL(/\/login$/)

            const cookies = await context.cookies()
            expect(cookies.some((cookie) => cookie.name === 'CM_ACCESS_TOKEN')).toBe(false)
            expect(cookies.some((cookie) => cookie.name === 'CM_REFRESH_TOKEN')).toBe(false)

            await page.goto('/account/settings')
            await expect(page).toHaveURL(/\/login$/)
        })
})
