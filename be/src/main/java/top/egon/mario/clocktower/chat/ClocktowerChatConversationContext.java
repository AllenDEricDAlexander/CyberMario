package top.egon.mario.clocktower.chat;

import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.im.po.ImConversationPo;

public record ClocktowerChatConversationContext(
        ImConversationPo conversation,
        String channelKey,
        String groupKey,
        Long roomId,
        ClocktowerGamePo game
) {

    public Long gameId() {
        return game == null ? null : game.getId();
    }
}
