package top.egon.mario.clocktower.board.service;

import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

import java.util.List;

public interface ClocktowerBoardService {

    BoardValidationResponse validate(ClocktowerBoardValidateRequest request);

    ClocktowerBoardGenerateResponse generate(ClocktowerBoardGenerateRequest request, RbacPrincipal principal);

    ClocktowerBoardConfigResponse save(ClocktowerBoardSaveRequest request, RbacPrincipal principal);

    List<ClocktowerBoardConfigResponse> list();

    void delete(Long boardId, RbacPrincipal principal);
}
