package top.egon.mario.im.facade;

import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.BlockUserCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.service.ImException;

@Component
public class DmFacade {

    public ConversationView openDm(OpenDmCommand command) {
        throw notImplemented();
    }

    public void block(BlockUserCommand command) {
        throw notImplemented();
    }

    public void unblock(BlockUserCommand command) {
        throw notImplemented();
    }

    private static ImException notImplemented() {
        return new ImException("IM_FACADE_NOT_IMPLEMENTED",
                "IM facade contract is defined; business implementation is pending");
    }
}
