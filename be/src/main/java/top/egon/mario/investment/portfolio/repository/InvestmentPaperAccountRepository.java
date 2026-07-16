package top.egon.mario.investment.portfolio.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;

import java.util.Optional;

/**
 * Owner-scoped persistence for private paper accounts.
 */
public interface InvestmentPaperAccountRepository extends JpaRepository<InvestmentPaperAccountPo, Long> {

    Optional<InvestmentPaperAccountPo> findByWorkspaceIdAndName(Long workspaceId, String name);

    @Query("""
            select account from InvestmentPaperAccountPo account, InvestmentWorkspacePo workspace
            where account.workspaceId = workspace.id
              and account.workspaceId = :workspaceId
              and workspace.ownerUserId = :ownerUserId
              and workspace.status = 'ACTIVE'
              and workspace.deleted = false
              and account.deleted = false
            """)
    Page<InvestmentPaperAccountPo> findOwnedAccounts(
            @Param("workspaceId") Long workspaceId,
            @Param("ownerUserId") Long ownerUserId,
            Pageable pageable);

    @Query("""
            select account from InvestmentPaperAccountPo account, InvestmentWorkspacePo workspace
            where account.workspaceId = workspace.id
              and account.id = :accountId
              and workspace.ownerUserId = :ownerUserId
              and workspace.deleted = false
              and account.deleted = false
            """)
    Optional<InvestmentPaperAccountPo> findOwnedAccount(
            @Param("accountId") Long accountId, @Param("ownerUserId") Long ownerUserId);
}
