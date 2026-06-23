import {describe, expect, test} from 'vitest'
import {appendChatChunk, mergeStreamText} from './chatMessageStream'
import type {ChatMessage} from './chatTypes'

const assistantMessage: ChatMessage = {
    id: 'assistant-1',
    role: 'assistant',
    content: '',
}

describe('chatMessageStream', () => {
    test('merges empty, cumulative, and repeated delta chunks', () => {
        expect(mergeStreamText('', '你')).toBe('你')
        expect(mergeStreamText('你', '你好')).toBe('你好')
        expect(mergeStreamText('哈', '哈')).toBe('哈哈')
        expect(mergeStreamText('你好', '好')).toBe('你好好')
        expect(mergeStreamText(
            "Hello! I'm CyberMario. How can I assist you today?",
            "Hello! I'm CyberMario. How can I assist you today?",
        )).toBe("Hello! I'm CyberMario. How can I assist you today?")
        expect(mergeStreamText('hello', '')).toBe('hello')
        expect(mergeStreamText('hello', null)).toBe('hello')
        expect(mergeStreamText('hello', undefined)).toBe('hello')
    })

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
