import {readFileSync} from 'node:fs'
import {describe, expect, test} from 'vitest'

const routesSource = readFileSync(new URL('./routes.tsx', import.meta.url), 'utf8')

describe('admin routes', () => {
    test('does not register the legacy global MCP tool policy page', () => {
        expect(routesSource).not.toContain("path: 'agent/mcp/tools'")
        expect(routesSource).not.toContain('McpToolListPage')
    })

    test('nutrition routes are registered under the protected admin layout', () => {
        const nutritionRoutes = {
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
            'nutrition/platform': 'PlatformNutritionConfigPage',
        }

        Object.entries(nutritionRoutes).forEach(([path, moduleName]) => {
            expect(routesSource).toContain(`path: '${path}'`)
            expect(routesSource).toContain(`lazy: () => import('../modules/nutrition/${moduleName}')`)
        })
        expect(routesSource).not.toContain("import('../modules/nutrition/pages/NutritionPlaceholderPage')")
    })
})
