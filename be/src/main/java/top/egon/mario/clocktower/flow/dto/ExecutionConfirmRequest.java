package top.egon.mario.clocktower.flow.dto;

public record ExecutionConfirmRequest(
        Boolean execute,
        ClocktowerExecutionDeathPolicy deathPolicy,
        String note
) {
}
