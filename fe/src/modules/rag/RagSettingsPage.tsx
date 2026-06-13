import {Card, Descriptions, Tag} from 'antd'
import {PageToolbar} from '../../components/PageToolbar'

function RagSettingsPage() {
    return (
        <>
            <PageToolbar
                description="RAG 全局配置当前由后端环境变量控制，后续可扩展为数据库配置。"
                title="RAG 设置"
            />
            <Card>
                <Descriptions column={1} bordered>
                    <Descriptions.Item label="Embedding 模型">
                        <Tag color="blue">text-embedding-v4</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Embedding 维度">
                        <Tag>1024</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="默认 TopK">
                        <Tag>6</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="默认阈值">
                        <Tag>0.55</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="文件存储">
                        <Tag color="green">LOCAL</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="后续扩展">
                        MinIO 存储、混合检索、rerank、评估集和成本统计。
                    </Descriptions.Item>
                </Descriptions>
            </Card>
        </>
    )
}

export const Component = RagSettingsPage
