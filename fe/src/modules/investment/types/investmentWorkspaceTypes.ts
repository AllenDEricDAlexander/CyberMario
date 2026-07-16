export type InvestmentWorkspaceResponse = {
    id: number
    name: string
    baseCurrency: string
    timezone: string
    status: string
    createdAt: string
}

export type CreateInvestmentWorkspaceRequest = {
    name: string
}

/**
 * Minimal selection snapshot. Detailed balances and risk state belong to the portfolio phase.
 */
export type InvestmentPaperAccountSelection = {
    id: number
    workspaceId: number
    name: string
    baseCurrency: string
    status: string
}
