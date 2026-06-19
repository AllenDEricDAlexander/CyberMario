package top.egon.mario.clocktower.board.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardGenerateRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardQuery;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardSaveRequest;
import top.egon.mario.clocktower.board.dto.request.ClocktowerBoardValidateRequest;
import top.egon.mario.clocktower.board.dto.response.BoardValidationResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardConfigResponse;
import top.egon.mario.clocktower.board.dto.response.ClocktowerBoardGenerateResponse;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerBoardService {

    BoardValidationResponse validate(ClocktowerBoardValidateRequest request);

    ClocktowerBoardGenerateResponse generate(ClocktowerBoardGenerateRequest request, RbacPrincipal principal);

    ClocktowerBoardConfigResponse save(ClocktowerBoardSaveRequest request, RbacPrincipal principal);

    Page<ClocktowerBoardConfigResponse> list(ClocktowerBoardQuery query, Pageable pageable, RbacPrincipal principal);

    ClocktowerBoardConfigResponse usableBoard(Long boardConfigId, String boardCode, RbacPrincipal principal);

    void delete(Long boardId, RbacPrincipal principal);
}
