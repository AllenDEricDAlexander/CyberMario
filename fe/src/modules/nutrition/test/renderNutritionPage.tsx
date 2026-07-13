import {render} from '@testing-library/react'
import {App} from 'antd'
import type {ReactElement} from 'react'
import {MemoryRouter} from 'react-router'

export function renderNutritionPage(ui: ReactElement) {
    return render(<App><MemoryRouter>{ui}</MemoryRouter></App>)
}
