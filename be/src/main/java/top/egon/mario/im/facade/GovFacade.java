package top.egon.mario.im.facade;

import org.springframework.stereotype.Component;
import top.egon.mario.im.facade.dto.command.AnnounceCommand;
import top.egon.mario.im.facade.dto.command.BanUserCommand;
import top.egon.mario.im.facade.dto.command.GlobalMuteCommand;
import top.egon.mario.im.facade.dto.command.MuteUserCommand;
import top.egon.mario.im.service.GovernanceService;

@Component
public class GovFacade {

    private final GovernanceService governanceService;

    public GovFacade(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    public void mute(MuteUserCommand command) {
        governanceService.mute(command);
    }

    public void globalMute(GlobalMuteCommand command) {
        governanceService.globalMute(command);
    }

    public void announce(AnnounceCommand command) {
        governanceService.announce(command);
    }

    public void ban(BanUserCommand command) {
        governanceService.ban(command);
    }
}
