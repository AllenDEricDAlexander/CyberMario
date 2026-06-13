export type TreeNodeLike<T> = T & {
    children?: TreeNodeLike<T>[]
}

export function flattenTree<T>(tree: TreeNodeLike<T>[]) {
    const result: TreeNodeLike<T>[] = []
    const visit = (nodes: TreeNodeLike<T>[]) => {
        nodes.forEach((node) => {
            result.push(node)
            if (node.children?.length) {
                visit(node.children)
            }
        })
    }
    visit(tree)
    return result
}

export function toTreeSelectOptions<T extends { children?: T[] }>(
    tree: T[],
    getValue: (item: T) => string | number,
    getTitle: (item: T) => string,
): { value: string | number; title: string; children?: { value: string | number; title: string }[] }[] {
    return tree.map((item) => ({
        value: getValue(item),
        title: getTitle(item),
        children: item.children?.length ? toTreeSelectOptions(item.children, getValue, getTitle) : undefined,
    }))
}
