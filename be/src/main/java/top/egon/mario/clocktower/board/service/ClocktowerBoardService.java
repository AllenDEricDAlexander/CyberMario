package top.egon.mario.clocktower.board.service;

import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;

public interface ClocktowerBoardService {

    BoardValidationResponse validate(ClocktowerBoardValidateRequest request);
}
