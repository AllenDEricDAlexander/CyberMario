import {renderToStaticMarkup} from 'react-dom/server'
import {describe, expect, test} from 'vitest'
import {DateTimeText} from './index'

describe('DateTimeText', () => {
    test('renders local date time by default', () => {
        const value = new Date(2026, 5, 16, 7, 8, 9)

        const markup = renderToStaticMarkup(<DateTimeText value={value}/>)

        expect(markup).toContain('2026-06-16 07:08:09')
    })

    test('supports date, time, and custom empty text', () => {
        expect(renderToStaticMarkup(<DateTimeText format="date" value={new Date(2026, 5, 16, 7, 8, 9)}/>))
            .toContain('2026-06-16')
        expect(renderToStaticMarkup(<DateTimeText format="time" value={new Date(2026, 5, 16, 7, 8, 9)}/>))
            .toContain('07:08:09')
        expect(renderToStaticMarkup(
            <DateTimeText format="YYYY-MM-DD HH:mm:ss" value={new Date(2026, 5, 16, 7, 8, 9)}/>,
        )).toContain('2026-06-16 07:08:09')
        expect(renderToStaticMarkup(<DateTimeText emptyText="暂无" value={undefined}/>)).toContain('暂无')
    })
})
