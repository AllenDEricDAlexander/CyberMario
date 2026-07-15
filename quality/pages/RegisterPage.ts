import type {Page} from '@playwright/test'
import type {TestIdentity} from '../models/test-identity'

export class RegisterPage {
    constructor(private readonly page: Page) {
    }

    async goto() {
        await this.page.goto('/register')
    }

    async fill(identity: TestIdentity, confirmPassword = identity.confirmPassword) {
        await this.page.getByLabel('账号', {exact: true}).fill(identity.accountNo)
        await this.page.getByLabel('用户名', {exact: true}).fill(identity.username)
        await this.page.getByLabel('昵称', {exact: true}).fill(identity.nickname)
        await this.page.getByLabel('密码', {exact: true}).fill(identity.password)
        await this.page.getByLabel('确认密码', {exact: true}).fill(confirmPassword)
        await this.page.getByLabel('邮箱', {exact: true}).fill(identity.email)
    }

    async submit() {
        await this.page.getByRole('button', {name: '注册并进入'}).click()
    }

    async register(identity: TestIdentity) {
        await this.fill(identity)
        const responsePromise = this.page.waitForResponse((response) =>
            response.request().method() === 'POST'
            && new URL(response.url()).pathname === '/api/auth/register')
        await this.submit()
        return await responsePromise
    }

    errorAlert() {
        return this.page.getByRole('alert')
    }
}
