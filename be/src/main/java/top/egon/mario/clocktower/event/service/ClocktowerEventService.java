package top.egon.mario.clocktower.event.service;

import top.egon.mario.clocktower.event.dto.ClocktowerEventAppendRequest;
import top.egon.mario.clocktower.event.dto.ClocktowerEventResponse;

public interface ClocktowerEventService {

    ClocktowerEventResponse append(ClocktowerEventAppendRequest request);
}
