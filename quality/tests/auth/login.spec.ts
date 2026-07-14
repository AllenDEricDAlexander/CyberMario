import {test, expect} from '../../fixtures/auth.fixture'
import {readApiEnvelope} from '../../models/api'
import {LoginPage} from '../../pages/LoginPage'

test.describe('login', () => {
    test('rejects a wrong password without establishing a session',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('wrong-password')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            const response = await loginPage.login(identity.accountNo, 'WrongPassword#2026')
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('AUTH_INVALID_CREDENTIALS')
            await expect(loginPage.errorAlert()).toBeVisible()
            await expect(page).toHaveURL(/\/login$/)
            const cookies = await context.cookies()
            expect(cookies.some((cookie) => cookie.name === 'CM_ACCESS_TOKEN')).toBe(false)
            expect(cookies.some((cookie) => cookie.name === 'CM_REFRESH_TOKEN')).toBe(false)
        })

    test('logs in with the registered account and writes HttpOnly cookies',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('login-success')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const loginPage = new LoginPage(page)
            await loginPage.goto()
            const response = await loginPage.login(identity.accountNo, identity.password)
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('0')
            await expect(page).toHaveURL(/\/chat$/)
            await expect(page.getByRole('button', {name: identity.nickname})).toBeVisible()
            const cookies = await context.cookies()
            expect(cookies.find((cookie) => cookie.name === 'CM_ACCESS_TOKEN')?.httpOnly).toBe(true)
            expect(cookies.find((cookie) => cookie.name === 'CM_REFRESH_TOKEN')?.httpOnly).toBe(true)
        })
})
