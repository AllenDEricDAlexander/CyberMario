import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {describe, expect, test, vi} from 'vitest'
import {PortfolioFillsTable} from './PortfolioFillsTable'
import {PortfolioLedgerTable} from './PortfolioLedgerTable'
import {PortfolioOrdersTable} from './PortfolioOrdersTable'
import {PortfolioPositionsTable} from './PortfolioPositionsTable'

describe('Investment portfolio fact tables', () => {
    test('renders only server-calculated position, liquidation, fill and ledger values', () => {
        render(<>
            <PortfolioPositionsTable positions={[{
                id: 71, instrumentId: 501, positionSide: 'LONG', quantity: '1', entryPrice: '100',
                leverage: '10', markPrice: '91.123456789123456789', liquidationPrice: '90.5',
                isolatedMargin: '10', maintenanceMargin: '0.5', realizedPnl: '0', fundingPnl: '-0.1',
                unrealizedPnl: '-8.876543210876543211', lastFillAt: null, lastMarginCheckAt: null,
            }]}/>
            <PortfolioFillsTable onPageChange={vi.fn()} page={{
                records: [{
                    id: 61, instrumentId: 501, marketBarOpenTime: '2026-07-17T00:00:00Z',
                    eventTime: '2026-07-17T00:01:00Z', side: 'SELL', actionType: 'CLOSE',
                    orderOrigin: 'LIQUIDATION', eventType: 'LIQUIDATION_FILL', price: '89.9',
                    quantity: '1', liquidation: true,
                }], page: 1, size: 100, total: 1, totalPages: 1,
            }}/>
            <PortfolioLedgerTable onPageChange={vi.fn()} page={{
                records: [{
                    id: 81, sequenceNo: 5, eventType: 'FUNDING', amount: '-0.1', balanceAfter: '99.9',
                    instrumentId: 501, referenceType: 'PAPER_FUNDING', referenceId: '71',
                    occurredAt: '2026-07-17T00:00:00Z',
                }], page: 1, size: 50, total: 1, totalPages: 1,
            }}/>
        </>)

        expect(screen.getByText('91.123456789123456789')).toBeTruthy()
        expect(screen.getByText('-8.876543210876543211')).toBeTruthy()
        expect(screen.getByText('90.5')).toBeTruthy()
        expect(screen.getByText('LIQUIDATION_FILL')).toBeTruthy()
        expect(screen.getByText('FUNDING')).toBeTruthy()
    })

    test('offers cancellation only for pending orders and forwards server page changes', async () => {
        const user = userEvent.setup()
        const onCancel = vi.fn()
        const onPageChange = vi.fn()
        render(<PortfolioOrdersTable onCancel={onCancel} onPageChange={onPageChange} page={{
            records: [
                {orderId: 41, status: 'PENDING_MATCH', submittedAt: '2026-07-17T00:00:00Z', matchedAt: null},
                {orderId: 42, status: 'FILLED', submittedAt: '2026-07-17T00:00:00Z', matchedAt: '2026-07-17T00:01:00Z'},
            ], page: 1, size: 20, total: 40, totalPages: 2,
        }}/>)

        expect(screen.getAllByRole('button', {name: /取\s*消/})).toHaveLength(1)
        await user.click(screen.getByRole('button', {name: /取\s*消/}))
        expect(onCancel).toHaveBeenCalledWith(41)
        await user.click(screen.getByTitle('2'))
        expect(onPageChange).toHaveBeenCalledWith(2, 20)
    })
})
