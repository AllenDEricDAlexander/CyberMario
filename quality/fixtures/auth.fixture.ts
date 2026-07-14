import {test as base, type Page} from '@playwright/test'
import {readApiEnvelope} from '../models/api'
import {createTestIdentity, type TestIdentity} from '../models/test-identity'
import {RegisterPage} from '../pages/RegisterPage'

type RegistrationResult = {
    code: string
    finalUrl: string
}

type AuthFixtures = {
    requestFailureDiagnostics: void
    identityFactory: (caseId: string) => TestIdentity
    registerThroughUi: (identity: TestIdentity) => Promise<RegistrationResult>
}

export const test = base.extend<AuthFixtures>({
    requestFailureDiagnostics: [async ({page}, use) => {
        attachRequestFailureDiagnostics(page)
        await use()
    }, {auto: true}],
    identityFactory: async ({}, use, testInfo) => {
        const runId = process.env.QUALITY_RUN_ID
        if (!runId) {
            throw new Error('QUALITY_RUN_ID is required; run tests through the quality runner')
        }
        await use((caseId) =>
            createTestIdentity(runId, `${testInfo.file}-${testInfo.title}-${caseId}`))
    },
    registerThroughUi: async ({browser, baseURL}, use) => {
        await use(async (identity) => {
            const context = await browser.newContext({baseURL})
            const page = await context.newPage()
            try {
                attachRequestFailureDiagnostics(page)
                const registerPage = new RegisterPage(page)
                await registerPage.goto()
                const response = await registerPage.register(identity)
                const envelope = await readApiEnvelope(response)
                await page.waitForURL(/\/chat$/)
                return {
                    code: envelope.code,
                    finalUrl: page.url(),
                }
            } finally {
                await context.close()
            }
        })
    },
})

export {expect} from '@playwright/test'

function attachRequestFailureDiagnostics(page: Page) {
    page.on('requestfailed', (request) => {
        const url = new URL(request.url())
        console.error('Browser request failed', {
            method: request.method(),
            url: `${url.origin}${url.pathname}`,
            reason: request.failure()?.errorText,
        })
    })
}
