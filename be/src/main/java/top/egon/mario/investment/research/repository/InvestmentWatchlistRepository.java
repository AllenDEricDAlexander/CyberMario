package top.egon.mario.investment.research.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.research.po.InvestmentWatchlistPo;

import java.util.Optional;

/**
 * Persistence operations that retain both workspace and owner data scope.
 */
public interface InvestmentWatchlistRepository extends JpaRepository<InvestmentWatchlistPo, Long> {

    @Query("""
            select watchlist from InvestmentWatchlistPo watchlist, InvestmentWorkspacePo workspace
            where watchlist.workspaceId = workspace.id
              and watchlist.workspaceId = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and watchlist.deleted = false
            """)
    Page<InvestmentWatchlistPo> findOwnedActiveWatchlists(
            @Param("workspaceId") Long workspaceId,
            @Param("ownerUserId") Long ownerUserId,
            Pageable pageable);

    @Query("""
            select watchlist from InvestmentWatchlistPo watchlist, InvestmentWorkspacePo workspace
            where watchlist.workspaceId = workspace.id
              and watchlist.workspaceId = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and watchlist.name = :name
            """)
    Optional<InvestmentWatchlistPo> findOwnedByBusinessKey(
            @Param("workspaceId") Long workspaceId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("name") String name);

    @Query("""
            select (count(watchlist) > 0)
            from InvestmentWatchlistPo watchlist, InvestmentWorkspacePo workspace
            where watchlist.workspaceId = workspace.id
              and watchlist.id = :watchlistId
              and watchlist.workspaceId = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and watchlist.deleted = false
            """)
    boolean existsOwnedActiveWatchlist(
            @Param("watchlistId") Long watchlistId,
            @Param("workspaceId") Long workspaceId,
            @Param("ownerUserId") Long ownerUserId);
}
