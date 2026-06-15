import {describe, expect, test} from 'vitest'
import {parseHeadersText, toMcpServerSubmitRequest} from './McpServerEditorDrawer'

describe('parseHeadersText', () => {
    test('returns an empty object for blank headers text', () => {
        expect(parseHeadersText('')).toEqual({})
        expect(parseHeadersText('   \n\t')).toEqual({})
        expect(parseHeadersText(undefined)).toEqual({})
    })

    test('preserves colons in header values after the first separator', () => {
        expect(parseHeadersText('Authorization: Bearer abc:def')).toEqual({
            Authorization: 'Bearer abc:def',
        })
    })

    test('ignores invalid lines without a colon', () => {
        expect(parseHeadersText('Authorization')).toEqual({})
    })
})

describe('toMcpServerSubmitRequest', () => {
    const baseValues = {
        serverCode: 'docs-mcp',
        serverName: 'Docs MCP',
        transportType: 'STREAMABLE_HTTP' as const,
        baseUrl: 'https://mcp.example.com',
        endpoint: '/mcp',
        connectTimeoutMs: 5000,
        requestTimeoutMs: 30000,
    }

    test('omits headers when unchanged masked edit headers are submitted', () => {
        expect(toMcpServerSubmitRequest({
            ...baseValues,
            headersText: 'Authorization: ******',
        }, {
            editing: true,
            initialHeadersText: 'Authorization: ******',
        })).toEqual({
            serverName: 'Docs MCP',
            transportType: 'STREAMABLE_HTTP',
            baseUrl: 'https://mcp.example.com',
            endpoint: '/mcp',
            connectTimeoutMs: 5000,
            requestTimeoutMs: 30000,
        })
    })

    test('sends empty headers when edit headers are cleared', () => {
        expect(toMcpServerSubmitRequest({
            ...baseValues,
            headersText: '   \n\t',
        }, {
            editing: true,
            initialHeadersText: 'Authorization: ******',
        })).toMatchObject({
            headers: {},
        })
    })

    test('sends parsed headers when edit headers are changed', () => {
        expect(toMcpServerSubmitRequest({
            ...baseValues,
            headersText: 'Authorization: Bearer new-token',
        }, {
            editing: true,
            initialHeadersText: 'Authorization: ******',
        })).toMatchObject({
            headers: {
                Authorization: 'Bearer new-token',
            },
        })
    })

    test('sends empty headers when create headers are blank', () => {
        expect(toMcpServerSubmitRequest({
            ...baseValues,
            headersText: '',
        }, {
            editing: false,
            initialHeadersText: '',
        })).toMatchObject({
            serverCode: 'docs-mcp',
            headers: {},
        })
    })

    test('preserves colons in submitted header values', () => {
        expect(toMcpServerSubmitRequest({
            ...baseValues,
            headersText: 'Authorization: Bearer abc:def',
        }, {
            editing: false,
            initialHeadersText: '',
        })).toMatchObject({
            headers: {
                Authorization: 'Bearer abc:def',
            },
        })
    })
})
