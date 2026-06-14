package top.egon.mario.agent.tools.arxiv;

import org.springframework.stereotype.Component;
import top.egon.mario.rbac.service.security.RbacPrincipal;

/**
 * Request-local user context used while the agent invokes arXiv tools.
 */
@Component
public class ArxivToolUserContext {

    private final ThreadLocal<RbacPrincipal> principalHolder = new ThreadLocal<>();

    public void set(RbacPrincipal principal) {
        principalHolder.set(principal);
    }

    public RbacPrincipal get() {
        return principalHolder.get();
    }

    public void clear() {
        principalHolder.remove();
    }

}
