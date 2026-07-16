package top.egon.mario.investment.research.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for creating or restoring a private Investment workspace.
 */
public record CreateInvestmentWorkspaceRequest(
        @NotBlank @Size(max = 128) String name
) {
}
