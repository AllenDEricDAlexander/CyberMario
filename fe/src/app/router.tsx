import {BasicLayout} from '../layouts/BasicLayout'
import {Home} from '../pages/Home'
import {Login} from '../pages/Login'

export function AppRouter() {
    const pathname = window.location.pathname

    if (pathname === '/login') {
        return <Login/>
    }

    return (
        <BasicLayout>
            <Home/>
        </BasicLayout>
    )
}
