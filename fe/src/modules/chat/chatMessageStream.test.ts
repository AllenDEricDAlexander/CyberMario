import {describe, expect, test} from 'vitest'
import {appendChatChunk} from './chatMessageStream'
import type {ChatMessage} from './chatTypes'

const assistantMessage: ChatMessage = {
    id: 'assistant-1',
    role: 'assistant',
    content: '',
}

describe('chatMessageStream', () => {
    test('appends delta message chunks', () => {
        const first = appendChatChunk(assistantMessage, {
            threadId: 'thread-1',
            type: 'message',
            message: '你',
        })
        const second = appendChatChunk(first, {
            threadId: 'thread-1',
            type: 'message',
            message: '好',
        })

        expect(second.content).toBe('你好')
    })

    test('replaces cumulative message chunks instead of duplicating text', () => {
        const first = appendChatChunk(assistantMessage, {
            threadId: 'thread-1',
            type: 'message',
            message: '你',
        })
        const second = appendChatChunk(first, {
            threadId: 'thread-1',
            type: 'message',
            message: '你好',
        })
        const third = appendChatChunk(second, {
            threadId: 'thread-1',
            type: 'message',
            message: '你好，我是 CyberMario。',
        })

        expect(third.content).toBe('你好，我是 CyberMario。')
    })

    test('keeps cumulative thinking chunks separate from final content', () => {
        const first = appendChatChunk(assistantMessage, {
            threadId: 'thread-1',
            type: 'think',
            message: '分析',
        })
        const second = appendChatChunk(first, {
            threadId: 'thread-1',
            type: 'think',
            message: '分析用户问题',
        })

        expect(second.content).toBe('')
        expect(second.thinkContent).toBe('分析用户问题')
    })

    test('error chunks replace any streamed assistant content', () => {
        const streamed = appendChatChunk(assistantMessage, {
            threadId: 'thread-1',
            type: 'message',
            message: '用户刚刚发送的问题',
        })
        const failed = appendChatChunk(streamed, {
            threadId: 'thread-1',
            type: 'error',
            message: '模型调用失败：url error',
        })

        expect(failed.content).toBe('模型调用失败：url error')
    })
})
