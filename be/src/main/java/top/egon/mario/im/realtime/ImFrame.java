package top.egon.mario.im.realtime;

/**
 * JSON frame exchanged by the IM WebSocket gateway.
 */
public record ImFrame(
        String type,
        String requestId,
        Object payload) {

    public static ImFrame server(String type, String requestId, Object payload) {
        return new ImFrame(type, requestId, payload);
    }
}
