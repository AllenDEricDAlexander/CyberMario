import {defineConfig, devices} from '@playwright/test'
import {serverEnvironment} from './scripts/start-server'
import {loadAutoEnvironment} from './support/autoEnvironment'

const environment = loadAutoEnvironment()
const backendUrl = `http://127.0.0.1:${environment.backendPort}`
const frontendUrl = `http://127.0.0.1:${environment.frontendPort}`
const inheritedEnvironment = Object.fromEntries(
    Object.entries(process.env)
        .filter((entry): entry is [string, string] => entry[1] !== undefined),
)

export default defineConfig({
    testDir: './tests',
    outputDir: 'test-results',
    fullyParallel: false,
    workers: 1,
    retries: 0,
    timeout: 60_000,
    expect: {
        timeout: 10_000,
    },
    reporter: environment.ci
        ? [
            ['list'],
            ['html', {outputFolder: 'playwright-report', open: 'never'}],
            ['junit', {outputFile: 'test-results/junit.xml'}],
        ]
        : [
            ['list'],
            ['html', {outputFolder: 'playwright-report', open: 'never'}],
        ],
    use: {
        ...devices['Desktop Chrome'],
        baseURL: frontendUrl,
        trace: 'retain-on-failure',
        screenshot: 'only-on-failure',
        video: environment.ci ? 'retain-on-failure' : 'off',
    },
    webServer: [
        {
            command: 'bun run scripts/start-server.ts backend',
            url: `${backendUrl}/actuator/health`,
            timeout: 240_000,
            reuseExistingServer: false,
            stdout: 'pipe',
            stderr: 'pipe',
            env: {
                ...inheritedEnvironment,
                SPRING_PROFILES_ACTIVE: 'auto',
                AUTO_BACKEND_PORT: String(environment.backendPort),
            },
        },
        {
            command: 'bun run scripts/start-server.ts frontend',
            url: `${frontendUrl}/login`,
            timeout: 120_000,
            reuseExistingServer: false,
            stdout: 'pipe',
            stderr: 'pipe',
            env: serverEnvironment('frontend', {
                ...inheritedEnvironment,
                AUTO_FRONTEND_PORT: String(environment.frontendPort),
                VITE_BACKEND_TARGET: backendUrl,
            }),
        },
    ],
})
