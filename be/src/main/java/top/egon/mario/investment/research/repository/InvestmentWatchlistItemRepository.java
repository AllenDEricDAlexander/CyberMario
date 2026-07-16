package top.egon.mario.investment.research.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.research.po.InvestmentWatchlistItemPo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Watchlist item persistence whose reads always retain watchlist, workspace and owner scope.
 */
public interface InvestmentWatchlistItemRepository extends JpaRepository<InvestmentWatchlistItemPo, Long> {

    @Query("""
            select item from InvestmentWatchlistItemPo item,
                 InvestmentWatchlistPo watchlist,
                 InvestmentWorkspacePo workspace
            where item.watchlistId = watchlist.id
              and watchlist.workspaceId = workspace.id
              and item.watchlistId = :watchlistId
              and watchlist.workspaceId = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and item.instrumentId = :instrumentId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and watchlist.deleted = false
            """)
    Optional<InvestmentWatchlistItemPo> findOwnedByBusinessKey(
            @Param("watchlistId") Long watchlistId,
            @Param("workspaceId") Long workspaceId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("instrumentId") Long instrumentId);

    @Query("""
            select item from InvestmentWatchlistItemPo item,
                 InvestmentWatchlistPo watchlist,
                 InvestmentWorkspacePo workspace
            where item.watchlistId = watchlist.id
              and watchlist.workspaceId = workspace.id
              and item.watchlistId in :watchlistIds
              and watchlist.workspaceId = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and watchlist.deleted = false
              and item.deleted = false
            order by item.sortNo asc, item.id asc
            """)
    List<InvestmentWatchlistItemPo> findOwnedActiveItems(
            @Param("watchlistIds") Collection<Long> watchlistIds,
            @Param("workspaceId") Long workspaceId,
            @Param("ownerUserId") Long ownerUserId);
}
