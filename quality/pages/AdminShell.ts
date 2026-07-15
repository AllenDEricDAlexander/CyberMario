import type {Page} from '@playwright/test'

export class AdminShell {
    constructor(private readonly page: Page) {
    }

    async logout(currentUserName: string) {
        await this.page.getByRole('button', {name: currentUserName}).click()
        const responsePromise = this.page.waitForResponse((response) =>
            response.request().method() === 'POST'
            && new URL(response.url()).pathname === '/api/auth/logout')
        await this.page.getByText('退出登录', {exact: true}).click()
        return await responsePromise
    }
}
