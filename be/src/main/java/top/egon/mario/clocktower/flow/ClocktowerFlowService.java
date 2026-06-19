package top.egon.mario.clocktower.flow;

import top.egon.mario.clocktower.flow.dto.ClocktowerFlowResponse;
import top.egon.mario.clocktower.flow.dto.CloseNominationRequest;
import top.egon.mario.clocktower.flow.dto.ExecutionConfirmRequest;
import top.egon.mario.clocktower.flow.dto.SkipNightTaskRequest;
import top.egon.mario.rbac.service.security.RbacPrincipal;

public interface ClocktowerFlowService {

    ClocktowerFlowResponse getFlow(Long roomId, RbacPrincipal principal);

    ClocktowerFlowResponse advance(Long roomId, RbacPrincipal principal);

    ClocktowerFlowResponse skipNightTask(Long roomId, Long taskId, SkipNightTaskRequest request, RbacPrincipal principal);

    ClocktowerFlowResponse closeNomination(Long roomId, Long nominationId, CloseNominationRequest request,
                                           RbacPrincipal principal);

    ClocktowerFlowResponse confirmExecution(Long roomId, ExecutionConfirmRequest request, RbacPrincipal principal);
}
