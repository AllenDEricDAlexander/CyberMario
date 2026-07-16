package top.egon.mario.investment.research;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.research.po.InvestmentWatchlistItemPo;
import top.egon.mario.investment.research.po.InvestmentWatchlistPo;
import top.egon.mario.investment.research.po.InvestmentWorkspacePo;
import top.egon.mario.investment.research.repository.InvestmentWatchlistItemRepository;
import top.egon.mario.investment.research.repository.InvestmentWatchlistRepository;
import top.egon.mario.investment.research.repository.InvestmentWorkspaceRepository;
import top.egon.mario.investment.research.service.InvestmentWatchlistService;
import top.egon.mario.investment.research.service.InvestmentWorkspaceService;
import top.egon.mario.investment.research.web.dto.AddInvestmentWatchlistItemRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentWatchlistRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentWorkspaceRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies private workspace and watchlist lifecycle rules.
 */
@ExtendWith(MockitoExtension.class)
class InvestmentWorkspaceServiceTests {

    @Mock
    private InvestmentWorkspaceRepository workspaceRepository;
    @Mock
    private InvestmentWatchlistRepository watchlistRepository;
    @Mock
    private InvestmentWatchlistItemRepository itemRepository;
    @Mock
    private InvestmentAccessService accessService;

    private InvestmentWorkspaceService workspaceService;
    private InvestmentWatchlistService watchlistService;

    @BeforeEach
    void setUp() {
        workspaceService = new InvestmentWorkspaceService(workspaceRepository);
        watchlistService = new InvestmentWatchlistService(
                watchlistRepository, itemRepository, accessService);
    }

    @Test
    void createsAWorkspaceWithExactlyOneOwnerAndListsOnlyThatOwner() {
        when(workspaceRepository.findOwnedByBusinessKey(101L, "Main")).thenReturn(Optional.empty());
        when(workspaceRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            InvestmentWorkspacePo workspace = invocation.getArgument(0);
            workspace.setId(11L);
            return workspace;
        });
        PageRequest pageable = PageRequest.of(0, 20);
        when(workspaceRepository.findOwnedActiveWorkspaces(101L, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        var created = workspaceService.create(101L, new CreateInvestmentWorkspaceRequest(" Main "));
        var page = workspaceService.list(101L, pageable);

        assertThat(created.id()).isEqualTo(11L);
        assertThat(created.name()).isEqualTo("Main");
        assertThat(created.baseCurrency()).isEqualTo("USDT");
        assertThat(page.getContent()).isEmpty();
        verify(workspaceRepository).findOwnedActiveWorkspaces(101L, pageable);
    }

    @Test
    void rejectsAnActiveSameOwnerWorkspaceName() {
        InvestmentWorkspacePo existing = workspace(11L, 101L, "Main", false);
        when(workspaceRepository.findOwnedByBusinessKey(101L, "Main")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> workspaceService.create(101L, new CreateInvestmentWorkspaceRequest("Main")))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.CONFLICT));
        verify(workspaceRepository, never()).saveAndFlush(any());
    }

    @Test
    void restoresTheDeletedWorkspaceBusinessKeyWithoutChangingItsOwner() {
        InvestmentWorkspacePo deleted = workspace(11L, 101L, "Main", true);
        when(workspaceRepository.findOwnedByBusinessKey(101L, "Main")).thenReturn(Optional.of(deleted));
        when(workspaceRepository.saveAndFlush(deleted)).thenReturn(deleted);

        var restored = workspaceService.create(101L, new CreateInvestmentWorkspaceRequest("Main"));

        assertThat(restored.id()).isEqualTo(11L);
        assertThat(deleted.getOwnerUserId()).isEqualTo(101L);
        assertThat(deleted.isDeleted()).isFalse();
        assertThat(deleted.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void restoresADeletedWatchlistInsteadOfInsertingTheSameBusinessKey() {
        InvestmentWatchlistPo deleted = watchlist(21L, 11L, "Core", true);
        when(watchlistRepository.findOwnedByBusinessKey(11L, 101L, "Core"))
                .thenReturn(Optional.of(deleted));
        when(watchlistRepository.saveAndFlush(deleted)).thenReturn(deleted);

        var restored = watchlistService.create(
                101L, 11L, new CreateInvestmentWatchlistRequest("Core", "restored"));

        assertThat(restored.id()).isEqualTo(21L);
        assertThat(deleted.isDeleted()).isFalse();
        assertThat(deleted.getDescription()).isEqualTo("restored");
        verify(accessService).requireWorkspaceOwner(11L, 101L);
        verify(watchlistRepository).saveAndFlush(deleted);
    }

    @Test
    void restoresADeletedItemThroughAnOwnerScopedLookup() {
        InvestmentWatchlistItemPo deleted = item(31L, 21L, 501L, true);
        when(itemRepository.findOwnedByBusinessKey(21L, 11L, 101L, 501L))
                .thenReturn(Optional.of(deleted));
        when(itemRepository.saveAndFlush(deleted)).thenReturn(deleted);

        var restored = watchlistService.addItem(
                101L, 11L, 21L, new AddInvestmentWatchlistItemRequest(501L, "BTC"));

        assertThat(restored.id()).isEqualTo(31L);
        assertThat(deleted.isDeleted()).isFalse();
        verify(accessService).requireWatchlistOwner(21L, 11L, 101L);
        verify(itemRepository).findOwnedByBusinessKey(21L, 11L, 101L, 501L);
    }

    @Test
    void returnsNotFoundWhenTheOwnedWatchlistDoesNotContainTheRequestedItem() {
        when(itemRepository.findOwnedByBusinessKey(21L, 11L, 101L, 501L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.removeItem(101L, 11L, 21L, 501L))
                .isInstanceOf(InvestmentException.class)
                .satisfies(error -> assertThat(((InvestmentException) error).getErrorCode())
                        .isEqualTo(InvestmentErrorCode.NOT_FOUND));

        verify(accessService).requireWatchlistOwner(21L, 11L, 101L);
        verify(itemRepository).findOwnedByBusinessKey(21L, 11L, 101L, 501L);
    }

    @Test
    void doesNotMisreportAnUnknownInstrumentForeignKeyAsADuplicateItem() {
        when(itemRepository.findOwnedByBusinessKey(21L, 11L, 101L, 999L))
                .thenReturn(Optional.empty());
        DataIntegrityViolationException foreignKeyFailure = new DataIntegrityViolationException(
                "fk_investment_watchlist_item_instrument");
        when(itemRepository.saveAndFlush(any())).thenThrow(foreignKeyFailure);

        assertThatThrownBy(() -> watchlistService.addItem(
                101L, 11L, 21L, new AddInvestmentWatchlistItemRequest(999L, "unknown")))
                .isSameAs(foreignKeyFailure);
    }

    private static InvestmentWorkspacePo workspace(Long id, Long ownerId, String name, boolean deleted) {
        InvestmentWorkspacePo po = new InvestmentWorkspacePo();
        po.setId(id);
        po.setOwnerUserId(ownerId);
        po.setName(name);
        po.setDeleted(deleted);
        return po;
    }

    private static InvestmentWatchlistPo watchlist(Long id, Long workspaceId, String name, boolean deleted) {
        InvestmentWatchlistPo po = new InvestmentWatchlistPo();
        po.setId(id);
        po.setWorkspaceId(workspaceId);
        po.setName(name);
        po.setDeleted(deleted);
        return po;
    }

    private static InvestmentWatchlistItemPo item(
            Long id, Long watchlistId, Long instrumentId, boolean deleted) {
        InvestmentWatchlistItemPo po = new InvestmentWatchlistItemPo();
        po.setId(id);
        po.setWatchlistId(watchlistId);
        po.setInstrumentId(instrumentId);
        po.setDeleted(deleted);
        return po;
    }
}
