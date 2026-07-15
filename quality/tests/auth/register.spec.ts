import {test, expect} from '../../fixtures/auth.fixture'
import {readApiEnvelope} from '../../models/api'
import {RegisterPage} from '../../pages/RegisterPage'

test.describe('registration', () => {
    test('validates required fields and password confirmation before requesting the backend',
        async ({page, identityFactory}) => {
            const requests: string[] = []
            page.on('request', (request) => {
                if (new URL(request.url()).pathname === '/api/auth/register') {
                    requests.push(request.url())
                }
            })
            const registerPage = new RegisterPage(page)
            await registerPage.goto()

            await registerPage.submit()
            await expect(page.getByText('请输入账号')).toBeVisible()
            await expect(page.getByText('请输入用户名')).toBeVisible()

            const identity = identityFactory('client-validation')
            await registerPage.fill(identity, 'different-password')
            await registerPage.submit()
            await expect(page.getByText('两次输入的密码不一致')).toBeVisible()
            expect(requests).toEqual([])
        })

    test('registers a unique user and establishes an HttpOnly browser session',
        async ({page, context, identityFactory}) => {
            const identity = identityFactory('registration-success')
            const registerPage = new RegisterPage(page)
            await registerPage.goto()

            const response = await registerPage.register(identity)
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('0')
            await expect(page).toHaveURL(/\/chat$/)
            await expect(page.getByRole('button', {name: identity.nickname})).toBeVisible()
            const cookies = await context.cookies()
            expect(cookies.find((cookie) => cookie.name === 'CM_ACCESS_TOKEN')?.httpOnly).toBe(true)
            expect(cookies.find((cookie) => cookie.name === 'CM_REFRESH_TOKEN')?.httpOnly).toBe(true)
        })

    test('rejects a duplicate account without creating another session',
        async ({page, context, identityFactory, registerThroughUi}) => {
            const identity = identityFactory('duplicate-account')
            const setup = await registerThroughUi(identity)
            expect(setup.code).toBe('0')

            const registerPage = new RegisterPage(page)
            await registerPage.goto()
            const response = await registerPage.register(identity)
            const envelope = await readApiEnvelope(response)

            expect(envelope.code).toBe('RBAC_USER_ACCOUNT_NO_DUPLICATED')
            await expect(registerPage.errorAlert()).toBeVisible()
            await expect(page).toHaveURL(/\/register$/)
            const cookies = await context.cookies()
            expect(cookies.some((cookie) => cookie.name === 'CM_ACCESS_TOKEN')).toBe(false)
            expect(cookies.some((cookie) => cookie.name === 'CM_REFRESH_TOKEN')).toBe(false)
        })
})
