import {defineConfig} from 'vitest/config'

export default defineConfig({
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/modules/nutrition/test/nutritionTestSetup.ts'],
        testTimeout: 15_000,
    },
})
