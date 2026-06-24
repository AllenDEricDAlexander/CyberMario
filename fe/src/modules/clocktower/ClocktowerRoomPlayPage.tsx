import {ReloadOutlined} from '@ant-design/icons'
import {Alert, Button, Card, Empty, Space} from 'antd'
import {useCallback, useEffect, useState} from 'react'
import {Link, useParams} from 'react-router'
import {reportGlobalError} from '../../app/globalError'
import {PageToolbar} from '../../components/PageToolbar'
import {voidify} from '../../utils/async'
import {getClocktowerGameView, getClocktowerRoom} from './clocktowerService'
import type {ClocktowerGameViewResponse, ClocktowerRoomResponse} from './clocktowerTypes'
import {GameRoomSurface} from './GameRoomPage'
import {StorytellerGameSurface} from './StorytellerGrimoirePage'

export type ClocktowerRoomPlayViewState = {
    room: ClocktowerRoomResponse
    gameView?: ClocktowerGameViewResponse | null
}

function ClocktowerRoomPlayPage() {
    const {roomId} = useParams()
    const numericRoomId = Number(roomId)
    const [state, setState] = useState<ClocktowerRoomPlayViewState | null>(null)
    const [loading, setLoading] = useState(false)

    const load = useCallback(async () => {
        if (!Number.isFinite(numericRoomId)) {
            return
        }
        setLoading(true)
        try {
            setState(await loadClocktowerRoomPlayView(numericRoomId))
        } catch (caught) {
            reportGlobalError(caught)
        } finally {
            setLoading(false)
        }
    }, [numericRoomId])

    useEffect(() => {
        void load()
    }, [load])

    if (!Number.isFinite(numericRoomId)) {
        return (
            <Alert
                showIcon
                title="房间地址无效"
                type="error"
            />
        )
    }

    if (!state) {
        return (
            <>
                <PageToolbar
                    actions={<Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(load)}>刷新</Button>}
                    description="正在解析房间和当前游戏视角。"
                    title="游戏入口"
                />
                <Card loading={loading}/>
            </>
        )
    }

    return (
        <ClocktowerRoomPlaySurface
            gameView={state.gameView}
            loading={loading}
            onReload={load}
            room={state.room}
        />
    )
}

export async function loadClocktowerRoomPlayView(roomId: number): Promise<ClocktowerRoomPlayViewState> {
    const room = await getClocktowerRoom(roomId)
    if (!room.currentGameId) {
        return {room, gameView: null}
    }
    const gameView = await getClocktowerGameView(room.currentGameId)
    return {room, gameView}
}

export function ClocktowerRoomPlaySurface({
    gameView,
    loading,
    onReload,
    room,
}: {
    room: ClocktowerRoomResponse
    gameView?: ClocktowerGameViewResponse | null
    loading?: boolean
    onReload?: () => Promise<void>
}) {
    if (!gameView) {
        return (
            <>
                <PageToolbar
                    actions={onReload && (
                        <Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(onReload)}>刷新</Button>
                    )}
                    description={`${room.status} · ${room.phase}`}
                    title={room.name}
                />
                <Card>
                    <Empty
                        description="游戏尚未开始"
                    >
                        <Space wrap>
                            <Link to={`/clocktower/rooms/${room.roomId}/lobby`}>
                                <Button type="primary">返回大厅</Button>
                            </Link>
                            <Link to="/clocktower/rooms">
                                <Button>房间列表</Button>
                            </Link>
                        </Space>
                    </Empty>
                </Card>
            </>
        )
    }

    if (gameView.viewerMode === 'STORYTELLER') {
        return <StorytellerGameSurface roomName={room.name} view={gameView}/>
    }

    if (gameView.viewerMode === 'PLAYER' || gameView.viewerMode === 'SPECTATOR') {
        return <GameRoomSurface roomName={room.name} view={gameView}/>
    }

    return (
        <>
            <PageToolbar
                actions={onReload && (
                    <Button icon={<ReloadOutlined/>} loading={loading} onClick={voidify(onReload)}>刷新</Button>
                )}
                description={`${room.status} · ${room.phase}`}
                title={room.name}
            />
            <Card>
                <Empty description="当前账号还没有可用游戏视角">
                    <Link to={`/clocktower/rooms/${room.roomId}/lobby`}>
                        <Button type="primary">返回大厅</Button>
                    </Link>
                </Empty>
            </Card>
        </>
    )
}

export const Component = ClocktowerRoomPlayPage
