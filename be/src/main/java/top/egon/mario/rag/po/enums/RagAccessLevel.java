package top.egon.mario.rag.po.enums;

/**
 * User-scoped access level for a RAG knowledge base.
 */
public enum RagAccessLevel {

    READ(10),
    WRITE(20),
    MANAGE(30);

    private final int rank;

    RagAccessLevel(int rank) {
        this.rank = rank;
    }

    /**
     * Returns whether this level includes the requested level.
     */
    public boolean allows(RagAccessLevel requestedLevel) {
        return rank >= requestedLevel.rank;
    }

}
