import {expect, test} from 'bun:test'
import {playwrightArguments} from './run-quality'

test('limits the initial runner to the Auth suite and forwards Playwright flags', () => {
    expect(playwrightArguments(['auth', '--headed']))
        .toEqual(['test', 'tests/auth', '--headed'])
    expect(playwrightArguments(['auth', 'tests/auth/register.spec.ts']))
        .toEqual(['test', 'tests/auth/register.spec.ts'])
    expect(() => playwrightArguments(['regression']))
        .toThrow('Only the auth suite is available')
})
