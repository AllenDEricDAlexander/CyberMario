import type {ReactNode} from 'react'

type BasicLayoutProps = {
    children: ReactNode
}

export function BasicLayout({children}: BasicLayoutProps) {
    return <main className="app-shell">{children}</main>
}
