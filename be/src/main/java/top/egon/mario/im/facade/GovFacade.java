package top.egon.mario.im.facade;

import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.AnnounceCommand;
import top.egon.mario.im.facade.dto.command.BanUserCommand;
import top.egon.mario.im.facade.dto.command.GlobalMuteCommand;
import top.egon.mario.im.facade.dto.command.MuteUserCommand;
import top.egon.mario.im.service.ImException;

@Component
public class GovFacade {

    public void mute(MuteUserCommand command) {
        throw notImplemented();
    }

    public void globalMute(GlobalMuteCommand command) {
        throw notImplemented();
    }

    public void announce(AnnounceCommand command) {
        throw notImplemented();
    }

    public void ban(BanUserCommand command) {
        throw notImplemented();
    }

    private static ImException notImplemented() {
        return new ImException("IM_FACADE_NOT_IMPLEMENTED",
                "IM facade contract is defined; business implementation is pending");
    }
}
