package top.egon.mario.clocktower.game.night.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.egon.mario.clocktower.common.enums.ClocktowerNightType;
import top.egon.mario.clocktower.common.enums.ClocktowerScriptCode;
import top.egon.mario.clocktower.game.night.service.ClocktowerNightOrderService;
import top.egon.mario.clocktower.game.po.ClocktowerGamePo;
import top.egon.mario.clocktower.game.po.ClocktowerGameSeatPo;
import top.egon.mario.clocktower.script.po.ClocktowerNightOrderPo;
import top.egon.mario.clocktower.script.repository.ClocktowerNightOrderRepository;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ClocktowerNightOrderServiceImpl implements ClocktowerNightOrderService {

    private final ClocktowerNightOrderRepository nightOrderRepository;

    @Override
    public List<ClocktowerNightOrderPo> currentOrders(ClocktowerGamePo game, List<ClocktowerGameSeatPo> seats) {
        ClocktowerScriptCode scriptCode = ClocktowerScriptCode.valueOf(game.getScriptCode());
        ClocktowerNightType nightType = game.getNightNo() <= 1
                ? ClocktowerNightType.FIRST_NIGHT : ClocktowerNightType.OTHER_NIGHT;
        List<String> roleCodes = seats.stream()
                .map(ClocktowerGameSeatPo::getRoleCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        return nightOrderRepository.findByScriptCodeAndNightTypeAndRoleCodeInAndDeletedFalseOrderBySortOrderAsc(
                scriptCode, nightType, roleCodes);
    }
}
