import {cleanup} from '@testing-library/react'
import {afterEach, vi} from 'vitest'

afterEach(cleanup)

Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
    })),
})

class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
}

Object.defineProperty(globalThis, 'ResizeObserver', {value: ResizeObserverStub})
Object.defineProperty(window, 'scrollTo', {value: vi.fn(), writable: true})

const getComputedStyle = window.getComputedStyle.bind(window)
Object.defineProperty(window, 'getComputedStyle', {
    value: (element: Element) => getComputedStyle(element),
    writable: true,
})
