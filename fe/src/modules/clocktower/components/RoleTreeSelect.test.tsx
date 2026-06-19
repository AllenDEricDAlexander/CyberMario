import {describe, expect, test} from 'vitest'
import {buildRoleTreeData, selectedRoleCountText} from './RoleTreeSelect'
import type {ClocktowerRoleResponse} from '../clocktowerTypes'

const baseRole = {
    scriptCode: 'TROUBLE_BREWING',
    name: 'Chef',
    alignment: 'GOOD',
    abilityText: '',
    enabled: true,
} satisfies Partial<ClocktowerRoleResponse>

describe('RoleTreeSelect helpers', () => {
    test('groups roles by localized role type with only role leaves checkable', () => {
        const treeData = buildRoleTreeData([
            {...baseRole, roleCode: 'IMP', roleName: '小恶魔', roleType: {code: 4, desc: '恶魔'}},
            {...baseRole, roleCode: 'CHEF', roleName: '厨师', roleType: 'TOWNSFOLK'},
            {...baseRole, roleCode: 'BARON', roleName: '男爵', roleType: {code: 3, desc: '爪牙'}},
        ] as ClocktowerRoleResponse[])

        expect(treeData.map((node) => node.title)).toEqual(['镇民', '爪牙', '恶魔'])
        expect(treeData[0]).toMatchObject({
            value: 'type-TOWNSFOLK',
            selectable: false,
            disableCheckbox: true,
        })
        expect(treeData[0].children?.[0]).toMatchObject({
            title: '厨师 (CHEF)',
            value: 'CHEF',
            selectable: true,
            checkable: true,
            searchText: '厨师 CHEF Chef',
        })
    })

    test('counts selected roles against player count', () => {
        expect(selectedRoleCountText(['CHEF', 'EMPATH'], 5)).toBe('已选择 2 / 5 个角色')
        expect(selectedRoleCountText([], 7)).toBe('已选择 0 / 7 个角色')
    })
})
