package top.egon.mario.agent.mcp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.mcp.po.McpToolConfigPo;

import java.util.List;
import java.util.Optional;

/**
 * Repository for discovered MCP tool configuration and policy records.
 */
public interface McpToolConfigRepository extends JpaRepository<McpToolConfigPo, Long> {

    Optional<McpToolConfigPo> findByIdAndDeletedFalse(Long id);

    Optional<McpToolConfigPo> findByServerIdAndToolNameAndDeletedFalse(Long serverId, String toolName);

    boolean existsByToolKeyAndDeletedFalse(String toolKey);

    List<McpToolConfigPo> findByServerIdAndDeletedFalseOrderByIdAsc(Long serverId);

    List<McpToolConfigPo> findByDeletedFalseOrderByIdDesc();

    List<McpToolConfigPo> findByEnabledTrueAndDeletedFalseOrderByIdAsc();

}
