package top.egon.mario.clocktower.game.night.service;

import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;

import java.util.List;

public interface ClocktowerNightOrderService {

    List<ClocktowerNightOrderPo> currentOrders(ClocktowerGamePo game, List<ClocktowerGameSeatPo> seats);
}
