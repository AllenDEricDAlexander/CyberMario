package top.egon.mario.rbac.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Replaces a relation collection with a submitted ID set.
 */
@Getter
@Setter
public class ReplaceIdsRequest {
    private Set<Long> ids = Set.of();
    private boolean syncButtonApis;
    private String remark;
}
