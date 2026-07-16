import {render} from '@testing-library/react'
import {App} from 'antd'
import type {ReactElement} from 'react'
import {MemoryRouter} from 'react-router'

export function renderInvestmentPage(ui: ReactElement, initialEntries = ['/investment/overview']) {
    return render(
        <App>
            <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>
        </App>,
    )
}
