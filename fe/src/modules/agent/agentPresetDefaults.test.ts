import {describe, expect, test} from 'vitest'
import {defaultAgentToolNames} from './agentPresetDefaults'

describe('agentPresetDefaults', () => {
    test('uses registered backend tool callback names by default', () => {
        expect(defaultAgentToolNames).toEqual([
            'searchWikipedia',
            'searchDuckDuckGoNews',
            'searchBraveWeb',
            'searchArxiv',
        ])
    })
})
