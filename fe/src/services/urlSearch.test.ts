import {describe, expect, test} from 'vitest'
import {buildSearchParams} from './urlSearch'

describe('buildSearchParams', () => {
    test('keeps meaningful scalar values and skips empty values', () => {
        expect(buildSearchParams({
            page: 1,
            size: 20,
            active: false,
            query: '',
            missing: undefined,
            empty: null,
        })).toBe('page=1&size=20&active=false')
    })

    test('encodes special characters through URLSearchParams', () => {
        expect(buildSearchParams({
            keyword: 'user admin',
            route: '/api/admin/users',
        })).toBe('keyword=user+admin&route=%2Fapi%2Fadmin%2Fusers')
    })
})
