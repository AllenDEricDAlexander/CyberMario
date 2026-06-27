package top.egon.mario.im.facade;

import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.BlockUserCommand;
import top.egon.mario.im.facade.dto.command.OpenDmCommand;
import top.egon.mario.im.facade.dto.view.ConversationView;
import top.egon.mario.im.service.DmService;

@Component
public class DmFacade {

    private final DmService dmService;

    public DmFacade(DmService dmService) {
        this.dmService = dmService;
    }

    public ConversationView openDm(OpenDmCommand command) {
        return dmService.openDm(command);
    }

    public void block(BlockUserCommand command) {
        dmService.block(command);
    }

    public void unblock(BlockUserCommand command) {
        dmService.unblock(command);
    }
}
