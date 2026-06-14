import {Card, Descriptions, Tag} from 'antd'
import {useEffect, useState} from 'react'
import {PageToolbar} from '../../components/PageToolbar'
import {getRagSettings} from './ragService'
import type {RagSettingsResponse} from './ragTypes'

function RagSettingsPage() {
    const [settings, setSettings] = useState<RagSettingsResponse | null>(null)

    useEffect(() => {
        void getRagSettings().then(setSettings)
    }, [])

    return (
        <>
            <PageToolbar
                description="RAG 全局配置当前由后端环境变量控制，后续可扩展为数据库配置。"
                title="RAG 设置"
            />
            <Card>
                <Descriptions column={1} bordered>
                    <Descriptions.Item label="Chat 模型">
                        <Tag color="blue">{settings?.chatModel ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Embedding 模型">
                        <Tag color="blue">{settings?.embeddingModel ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Embedding 维度">
                        <Tag>{settings?.embeddingDimension ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="默认检索模式">
                        <Tag color="green">{settings?.defaultSearchMode ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="默认 TopK">
                        <Tag>{settings?.defaultTopK ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="候选 TopK">
                        <Tag>{settings?.candidateTopK ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="上下文 TopK">
                        <Tag>{settings?.contextTopK ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="默认阈值">
                        <Tag>{settings?.defaultSimilarityThreshold ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Rerank">
                        <Tag color={settings?.rerankEnabled ? 'purple' : 'default'}>
                            {settings?.rerankEnabled ? '默认开启' : '默认关闭'}
                        </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Rerank 模型">
                        <Tag>{settings?.rerankModel ?? '-'}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="文件存储">
                        <Tag color="green">LOCAL</Tag>
                    </Descriptions.Item>
                </Descriptions>
            </Card>
        </>
    )
}

export const Component = RagSettingsPage
