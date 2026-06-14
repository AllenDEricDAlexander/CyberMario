type AsyncErrorHandler = (error: unknown) => void

const errorHandlers = new Set<AsyncErrorHandler>()

export function registerAsyncErrorHandler(handler: AsyncErrorHandler) {
    errorHandlers.add(handler)
    return () => {
        errorHandlers.delete(handler)
    }
}

type UnhandledRejectionTarget = {
    addEventListener: (type: 'unhandledrejection', listener: (event: PromiseRejectionEvent) => void) => void
    removeEventListener: (type: 'unhandledrejection', listener: (event: PromiseRejectionEvent) => void) => void
}

export function registerUnhandledRejectionReporter(
    handler: AsyncErrorHandler,
    target: UnhandledRejectionTarget = window,
) {
    const listener = (event: PromiseRejectionEvent) => {
        handler(event.reason)
        event.preventDefault()
    }
    target.addEventListener('unhandledrejection', listener)
    return () => {
        target.removeEventListener('unhandledrejection', listener)
    }
}

export function voidify<T extends unknown[]>(handler: (...args: T) => Promise<void>) {
    return (...args: T) => {
        void handler(...args).catch(reportAsyncError)
    }
}

function reportAsyncError(error: unknown) {
    errorHandlers.forEach((handler) => handler(error))
}
