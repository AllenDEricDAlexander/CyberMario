export function voidify<T extends unknown[]>(handler: (...args: T) => Promise<void>) {
    return (...args: T) => {
        void handler(...args)
    }
}
