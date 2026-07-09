package top.egon.mario.clocktower.agent.runtime;

public final class ClocktowerAgentTriggerType {

    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String PHASE_CHANGED = "PHASE_CHANGED";
    public static final String MIC_TURN_STARTED = "MIC_TURN_STARTED";
    public static final String MIC_GRAB_OPENED = "MIC_GRAB_OPENED";
    public static final String NOMINATION_OPENED = "NOMINATION_OPENED";
    public static final String VOTE_WINDOW_OPENED = "VOTE_WINDOW_OPENED";
    public static final String NIGHT_TASK_OPENED = "NIGHT_TASK_OPENED";
    public static final String PUBLIC_EVENT_APPENDED = "PUBLIC_EVENT_APPENDED";
    public static final String PRIVATE_INFO_RECEIVED = "PRIVATE_INFO_RECEIVED";
    public static final String PLAYER_DIED = "PLAYER_DIED";
    public static final String TIMER_TICK = "TIMER_TICK";
    public static final String ST_RUN_NOW = "ST_RUN_NOW";

    private ClocktowerAgentTriggerType() {
    }
}
