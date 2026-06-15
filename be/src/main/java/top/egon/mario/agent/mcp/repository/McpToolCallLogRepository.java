package top.egon.mario.agent.mcp.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.mcp.po.McpToolCallLogPo;

import java.util.Optional;

/**
 * Repository for MCP tool execution audit logs.
 */
public interface McpToolCallLogRepository extends JpaRepository<McpToolCallLogPo, Long> {

    Optional<McpToolCallLogPo> findById(Long id);

    Page<McpToolCallLogPo> findAllByOrderByIdDesc(Pageable pageable);

}
