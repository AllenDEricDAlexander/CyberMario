package top.egon.mario.agent.mcp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.agent.mcp.po.McpServerConfigPo;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MCP server configuration records.
 */
public interface McpServerConfigRepository extends JpaRepository<McpServerConfigPo, Long> {

    Optional<McpServerConfigPo> findByIdAndDeletedFalse(Long id);

    Optional<McpServerConfigPo> findByServerCodeAndDeletedFalse(String serverCode);

    boolean existsByServerCodeAndDeletedFalse(String serverCode);

    List<McpServerConfigPo> findByDeletedFalseOrderByIdDesc();

    List<McpServerConfigPo> findByEnabledTrueAndDeletedFalseOrderByIdAsc();

}
