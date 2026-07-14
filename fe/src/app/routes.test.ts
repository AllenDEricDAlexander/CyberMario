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
})
