import type {Page} from '@playwright/test'

export class LoginPage {
    constructor(private readonly page: Page) {
    }

    async goto() {
        await this.page.goto('/login')
    }

    async login(account: string, password: string) {
        await this.page.getByLabel('账号或邮箱', {exact: true}).fill(account)
        await this.page.getByLabel('密码', {exact: true}).fill(password)
        const responsePromise = this.page.waitForResponse((response) =>
            response.request().method() === 'POST'
            && new URL(response.url()).pathname === '/api/auth/login')
        await this.page.getByRole('button', {name: '进入工作台'}).click()
        return await responsePromise
    }

    errorAlert() {
        return this.page.getByRole('alert')
    }
}
