import {describe, expect, test} from 'vitest'
import {resolvePageLoadParams, toPageDataState} from './pageDataState'

describe('pageDataState', () => {
    test('converts backend page results into frontend page state', () => {
        expect(toPageDataState({
            records: [{id: 1}, {id: 2}],
            page: 3,
            size: 40,
            total: 82,
            totalPages: 3,
        })).toEqual({
            records: [{id: 1}, {id: 2}],
            page: 3,
            size: 40,
            total: 82,
        })
    })

    test('resolves explicit page load params before current state', () => {
        expect(resolvePageLoadParams({page: 2, size: 20}, 5, 50)).toEqual({
            page: 5,
            size: 50,
        })
    })

    test('falls back to current page state when params are omitted', () => {
        expect(resolvePageLoadParams({page: 2, size: 20})).toEqual({
            page: 2,
            size: 20,
        })
    })
})
