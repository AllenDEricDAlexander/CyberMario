import {describe, expect, test} from 'vitest'
import {
    createAssistantPlaceholder,
    getRequestErrorMessage,
    markMessageInfoAborted,
    markMessageAborted,
    markMessageSucceeded,
    toMessageInfo,
    updateAssistantMessage,
    useXChatWorkspace,
} from './useXChatWorkspace'
import type {ChatWorkspaceMessage} from './chatWorkspaceTypes'

type IsExact<Type, Expected> =
    (<Value>() => Value extends Type ? 1 : 2) extends
    (<Value>() => Value extends Expected ? 1 : 2)
        ? (<Value>() => Value extends Expected ? 1 : 2) extends
        (<Value>() => Value extends Type ? 1 : 2)
            ? true
            : false
        : false
type HasKey<Type, Key extends PropertyKey> = Key extends keyof Type ? true : false

type UseXChatWorkspaceResult = ReturnType<typeof useXChatWorkspace>

const adapterReturnContract: {
    doesNotExposeSdkOnRequest: IsExact<HasKey<UseXChatWorkspaceResult, 'onRequest'>, false>
    doesNotExposeSdkOnReload: IsExact<HasKey<UseXChatWorkspaceResult, 'onReload'>, false>
    doesNotExposeSdkQueueRequest: IsExact<HasKey<UseXChatWorkspaceResult, 'queueRequest'>, false>
    exposesLocalAbortCommand: IsExact<UseXChatWorkspaceResult['abort'], () => void>
} = {
    doesNotExposeSdkOnRequest: true,
    doesNotExposeSdkOnReload: true,
    doesNotExposeSdkQueueRequest: true,
    exposesLocalAbortCommand: true,
}

const userMessage: ChatWorkspaceMessage = {
    id: 'user-1',
    role: 'user',
    content: 'Hello',
    status: 'local',
}

const assistantMessage: ChatWorkspaceMessage = {
    id: 'assistant-1',
    role: 'assistant',
    content: 'Hi',
    status: 'success',
    traceId: 'trace-1',
}

describe('useXChatWorkspace helpers', () => {
    test('exposes only the safe adapter return contract', () => {
        expect(adapterReturnContract).toEqual({
            doesNotExposeSdkOnRequest: true,
            doesNotExposeSdkOnReload: true,
            doesNotExposeSdkQueueRequest: true,
            exposesLocalAbortCommand: true,
        })
    })

    test('converts workspace messages to SDK message info', () => {
        expect(toMessageInfo(assistantMessage)).toEqual({
            id: 'assistant-1',
            message: assistantMessage,
            status: 'success',
        })
    })

    test('creates assistant placeholders with loading status and question context', () => {
        expect(createAssistantPlaceholder('assistant-2', 'What is new?')).toEqual({
            id: 'assistant-2',
            role: 'assistant',
            content: '',
            question: 'What is new?',
            status: 'loading',
        })
    })

    test('updates only the targeted assistant message', () => {
        const messages = [userMessage, assistantMessage]
        const updated = updateAssistantMessage(messages, 'assistant-1', message => ({
            ...message,
            content: `${message.content} there`,
            status: 'updating',
        }))

        expect(updated).toEqual([
            userMessage,
            {
                ...assistantMessage,
                content: 'Hi there',
                status: 'updating',
            },
        ])
        expect(updated[0]).toBe(userMessage)
    })

    test('keeps messages unchanged when the target assistant message is missing', () => {
        const messages = [userMessage, assistantMessage]

        expect(updateAssistantMessage(messages, 'missing', message => ({...message, content: 'changed'}))).toEqual(messages)
    })

    test('marks empty aborted messages with fallback stopped content', () => {
        expect(markMessageAborted({...assistantMessage, content: '', status: 'loading'})).toEqual({
            ...assistantMessage,
            content: 'Stopped.',
            status: 'abort',
        })
    })

    test('marks non-empty aborted messages without replacing content', () => {
        expect(markMessageAborted({...assistantMessage, content: 'Partial answer', status: 'updating'})).toEqual({
            ...assistantMessage,
            content: 'Partial answer',
            status: 'abort',
        })
    })

    test('marks SDK message info aborted while preserving streamed content', () => {
        const messageInfo = toMessageInfo({
            ...assistantMessage,
            content: 'Partial answer',
            status: 'updating',
        })

        expect(markMessageInfoAborted(messageInfo)).toEqual({
            id: 'assistant-1',
            message: {
                ...assistantMessage,
                content: 'Partial answer',
                status: 'abort',
            },
            status: 'abort',
        })
    })

    test('marks active messages as successful while preserving terminal failure statuses', () => {
        expect(markMessageSucceeded({...assistantMessage, status: 'updating'}).status).toBe('success')
        expect(markMessageSucceeded({...assistantMessage, status: 'error'}).status).toBe('error')
        expect(markMessageSucceeded({...assistantMessage, status: 'abort'}).status).toBe('abort')
    })

    test('derives request error messages from unknown rejection values', () => {
        expect(getRequestErrorMessage(new Error('Network failed'))).toBe('Network failed')
        expect(getRequestErrorMessage('Plain failure')).toBe('Plain failure')
        expect(getRequestErrorMessage({message: 'Object failure'})).toBe('Object failure')
        expect(getRequestErrorMessage(undefined)).toBe('Request failed.')
    })
})
