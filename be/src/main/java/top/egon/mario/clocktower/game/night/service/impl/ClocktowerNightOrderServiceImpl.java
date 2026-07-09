package top.egon.mario.clocktower.game.night.service.impl;

import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightOrderService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;

import java.util.List;

@Service
public class ClocktowerNightOrderServiceImpl implements ClocktowerNightOrderService {

    @Override
    public List<ClocktowerNightOrderPo> currentOrders(ClocktowerGamePo game, List<ClocktowerGameSeatPo> seats) {
        return List.of();
    }
}
