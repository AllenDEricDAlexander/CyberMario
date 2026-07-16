package top.egon.mario.investment.portfolio.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.investment.portfolio.po.InvestmentPaperAccountPo;

import jakarta.persistence.LockModeType;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select account from InvestmentPaperAccountPo account
            where account.id = :accountId
              and account.workspaceId = :workspaceId
              and account.deleted = false
            """)
    Optional<InvestmentPaperAccountPo> findByIdAndWorkspaceIdForUpdate(
            @Param("accountId") Long accountId, @Param("workspaceId") Long workspaceId);
}
