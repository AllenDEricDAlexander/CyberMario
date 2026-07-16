package top.egon.mario.investment.trading.matching.model;

/**
 * Same-bar event priority; lower values execute first.
 */
public enum SimulationEventType {
    LIQUIDATION(0),
    FUNDING(10),
    ORDER_MATCH(20);

    private final int priority;

    SimulationEventType(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
