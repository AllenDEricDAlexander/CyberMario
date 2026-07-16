import {readFileSync} from 'node:fs'
import {resolve} from 'node:path'
import {describe, expect, test} from 'vitest'

const routesSource = readFileSync(resolve(process.cwd(), 'src/app/routes.tsx'), 'utf8')

describe('admin routes', () => {
    test('does not register the legacy global MCP tool policy page', () => {
        expect(routesSource).not.toContain("path: 'agent/mcp/tools'")
        expect(routesSource).not.toContain('McpToolListPage')
    })

    test('registers family nutrition routes under the workspace layout and keeps platform separate', () => {
        const familyRoutes = {
            'nutrition/home': 'NutritionHomePage',
            'nutrition/families': 'ClanFamilyPage',
            'nutrition/members': 'MemberHealthPage',
            'nutrition/recipes': 'RecipeLibraryPage',
            'nutrition/ai-menus': 'AiMenuPage',
            'nutrition/confirmations': 'MealConfirmationPage',
            'nutrition/meal-summary': 'MealSummaryPage',
            'nutrition/shopping': 'ShoppingListPage',
            'nutrition/budget': 'BudgetPage',
            'nutrition/records': 'NutritionRecordPage',
        }

        expect(routesSource).toContain("import {NutritionWorkspaceLayout} from '../modules/nutrition/NutritionWorkspaceLayout'")
        const layoutStart = routesSource.indexOf('element: <NutritionWorkspaceLayout/>')
        const platformStart = routesSource.indexOf("path: 'nutrition/platform'")
        expect(layoutStart).toBeGreaterThan(-1)
        expect(platformStart).toBeGreaterThan(layoutStart)
        const familyLayoutSource = routesSource.slice(layoutStart, platformStart)
        Object.entries(familyRoutes).forEach(([path, moduleName]) => {
            expect(familyLayoutSource).toContain(`path: '${path}'`)
            expect(familyLayoutSource).toContain(`lazy: () => import('../modules/nutrition/${moduleName}')`)
        })
        expect(familyLayoutSource).not.toContain('PlatformNutritionConfigPage')
        expect(routesSource).toContain("lazy: () => import('../modules/nutrition/PlatformNutritionConfigPage')")
        expect(routesSource).not.toContain("import('../modules/nutrition/pages/NutritionPlaceholderPage')")
    })

    test('registers lazy Investment workspace routes and keeps the platform route independent', () => {
        const workspaceRoutes = {
            'investment/overview': 'overview/InvestmentOverviewPage',
            'investment/market': 'market/InvestmentMarketPage',
            'investment/instruments/:instrumentId': 'instrument/InvestmentInstrumentPage',
            'investment/research': 'research/InvestmentResearchPage',
            'investment/quant': 'quant/InvestmentQuantPage',
            'investment/portfolio': 'portfolio/InvestmentPortfolioPage',
            'investment/agent': 'agent/InvestmentAgentPage',
        }

        expect(routesSource).toContain(
            "import {InvestmentWorkspaceLayout} from '../modules/investment/InvestmentWorkspaceLayout'",
        )
        const layoutStart = routesSource.indexOf('element: <InvestmentWorkspaceLayout/>')
        const platformStart = routesSource.indexOf("path: 'investment/platform'")
        expect(layoutStart).toBeGreaterThan(-1)
        expect(platformStart).toBeGreaterThan(layoutStart)
        const workspaceLayoutSource = routesSource.slice(layoutStart, platformStart)
        Object.entries(workspaceRoutes).forEach(([path, modulePath]) => {
            expect(workspaceLayoutSource).toContain(`path: '${path}'`)
            expect(workspaceLayoutSource).toContain(
                `lazy: () => import('../modules/investment/${modulePath}')`,
            )
        })
        expect(workspaceLayoutSource).not.toContain('InvestmentPlatformPage')
        expect(routesSource).toContain(
            "lazy: () => import('../modules/investment/platform/InvestmentPlatformPage')",
        )
    })
})
