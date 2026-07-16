package top.egon.mario.investment.research.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.research.po.InvestmentWorkspacePo;

import java.util.Optional;

/**
 * Owner-scoped persistence for private Investment workspaces.
 */
public interface InvestmentWorkspaceRepository extends JpaRepository<InvestmentWorkspacePo, Long> {

    @Query("""
            select workspace from InvestmentWorkspacePo workspace
            where workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
            """)
    Page<InvestmentWorkspacePo> findOwnedActiveWorkspaces(
            @Param("ownerUserId") Long ownerUserId, Pageable pageable);

    @Query("""
            select workspace from InvestmentWorkspacePo workspace
            where workspace.ownerUserId = :ownerUserId
              and workspace.name = :name
            """)
    Optional<InvestmentWorkspacePo> findOwnedByBusinessKey(
            @Param("ownerUserId") Long ownerUserId, @Param("name") String name);

    @Query("""
            select (count(workspace) > 0) from InvestmentWorkspacePo workspace
            where workspace.id = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
            """)
    boolean existsOwnedActiveWorkspace(
            @Param("workspaceId") Long workspaceId, @Param("ownerUserId") Long ownerUserId);
}
