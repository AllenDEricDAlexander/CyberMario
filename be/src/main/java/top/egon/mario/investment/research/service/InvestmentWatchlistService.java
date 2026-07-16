package top.egon.mario.investment.research.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.egon.mario.investment.common.InvestmentErrorCode;
import top.egon.mario.investment.common.InvestmentException;
import top.egon.mario.investment.common.access.InvestmentAccessService;
import top.egon.mario.investment.research.po.InvestmentWatchlistItemPo;
import top.egon.mario.investment.research.po.InvestmentWatchlistPo;
import top.egon.mario.investment.research.repository.InvestmentWatchlistItemRepository;
import top.egon.mario.investment.research.repository.InvestmentWatchlistRepository;
import top.egon.mario.investment.research.web.dto.AddInvestmentWatchlistItemRequest;
import top.egon.mario.investment.research.web.dto.CreateInvestmentWatchlistRequest;
import top.egon.mario.investment.research.web.dto.InvestmentWatchlistItemResponse;
import top.egon.mario.investment.research.web.dto.InvestmentWatchlistResponse;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages watchlists with owner scope repeated at access and persistence boundaries.
 */
@Service
@RequiredArgsConstructor
public class InvestmentWatchlistService {

    private final InvestmentWatchlistRepository watchlistRepository;
    private final InvestmentWatchlistItemRepository itemRepository;
    private final InvestmentAccessService accessService;

    /**
     * Lists watchlists and their items without per-watchlist query fan-out.
     */
    @Transactional(readOnly = true)
    public Page<InvestmentWatchlistResponse> list(Long actorId, Long workspaceId, Pageable pageable) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        Page<InvestmentWatchlistPo> watchlists = watchlistRepository
                .findOwnedActiveWatchlists(workspaceId, actorId, pageable);
        List<Long> watchlistIds = watchlists.getContent().stream()
                .map(InvestmentWatchlistPo::getId)
                .toList();
        Map<Long, List<InvestmentWatchlistItemResponse>> itemsByWatchlist = watchlistIds.isEmpty()
                ? Map.of()
                : itemRepository.findOwnedActiveItems(watchlistIds, workspaceId, actorId).stream()
                .collect(Collectors.groupingBy(
                        InvestmentWatchlistItemPo::getWatchlistId,
                        Collectors.mapping(InvestmentWatchlistService::toItemResponse, Collectors.toList())));
        return watchlists.map(watchlist -> toResponse(
                watchlist, itemsByWatchlist.getOrDefault(watchlist.getId(), Collections.emptyList())));
    }

    /**
     * Creates a watchlist or restores its soft-deleted business key.
     */
    @Transactional
    public InvestmentWatchlistResponse create(
            Long actorId, Long workspaceId, CreateInvestmentWatchlistRequest request) {
        accessService.requireWorkspaceOwner(workspaceId, actorId);
        String name = normalizedName(request == null ? null : request.name());
        String description = normalizedText(request == null ? null : request.description(), 512, "description");
        InvestmentWatchlistPo watchlist = watchlistRepository
                .findOwnedByBusinessKey(workspaceId, actorId, name)
                .map(existing -> restore(existing, description))
                .orElseGet(() -> newWatchlist(workspaceId, name, description));
        try {
            return toResponse(watchlistRepository.saveAndFlush(watchlist), List.of());
        } catch (DataIntegrityViolationException exception) {
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Watchlist name already exists in this workspace", exception);
        }
    }

    /**
     * Adds an instrument or restores the existing soft-deleted membership row.
     */
    @Transactional
    public InvestmentWatchlistItemResponse addItem(
            Long actorId, Long workspaceId, Long watchlistId, AddInvestmentWatchlistItemRequest request) {
        accessService.requireWatchlistOwner(watchlistId, workspaceId, actorId);
        Long instrumentId = request == null ? null : request.instrumentId();
        if (instrumentId == null || instrumentId <= 0) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, "Instrument id is required");
        }
        String note = normalizedText(request.note(), 512, "note");
        InvestmentWatchlistItemPo item = itemRepository
                .findOwnedByBusinessKey(watchlistId, workspaceId, actorId, instrumentId)
                .map(existing -> restore(existing, note))
                .orElseGet(() -> newItem(watchlistId, instrumentId, note));
        try {
            return toItemResponse(itemRepository.saveAndFlush(item));
        } catch (DataIntegrityViolationException exception) {
            if (!causedByConstraint(exception, "uk_investment_watchlist_item_instrument")) {
                throw exception;
            }
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Instrument already exists in this watchlist", exception);
        }
    }

    /**
     * Soft-deletes an owner-scoped membership while retaining its unique business key for recovery.
     */
    @Transactional
    public void removeItem(Long actorId, Long workspaceId, Long watchlistId, Long instrumentId) {
        accessService.requireWatchlistOwner(watchlistId, workspaceId, actorId);
        InvestmentWatchlistItemPo item = itemRepository
                .findOwnedByBusinessKey(watchlistId, workspaceId, actorId, instrumentId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new InvestmentException(
                        InvestmentErrorCode.NOT_FOUND, "Watchlist item not found"));
        item.setDeleted(true);
        itemRepository.save(item);
    }

    private static InvestmentWatchlistPo restore(InvestmentWatchlistPo watchlist, String description) {
        if (!watchlist.isDeleted()) {
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Watchlist name already exists in this workspace");
        }
        watchlist.setDescription(description);
        watchlist.setDeleted(false);
        return watchlist;
    }

    private static InvestmentWatchlistItemPo restore(InvestmentWatchlistItemPo item, String note) {
        if (!item.isDeleted()) {
            throw new InvestmentException(
                    InvestmentErrorCode.CONFLICT, "Instrument already exists in this watchlist");
        }
        item.setNote(note);
        item.setDeleted(false);
        return item;
    }

    private static InvestmentWatchlistPo newWatchlist(Long workspaceId, String name, String description) {
        InvestmentWatchlistPo watchlist = new InvestmentWatchlistPo();
        watchlist.setWorkspaceId(workspaceId);
        watchlist.setName(name);
        watchlist.setDescription(description);
        return watchlist;
    }

    private static InvestmentWatchlistItemPo newItem(Long watchlistId, Long instrumentId, String note) {
        InvestmentWatchlistItemPo item = new InvestmentWatchlistItemPo();
        item.setWatchlistId(watchlistId);
        item.setInstrumentId(instrumentId);
        item.setNote(note);
        return item;
    }

    private static InvestmentWatchlistResponse toResponse(
            InvestmentWatchlistPo watchlist, List<InvestmentWatchlistItemResponse> items) {
        return new InvestmentWatchlistResponse(
                watchlist.getId(), watchlist.getWorkspaceId(), watchlist.getName(), watchlist.getDescription(),
                watchlist.getSortNo(), items, watchlist.getCreatedAt());
    }

    private static InvestmentWatchlistItemResponse toItemResponse(InvestmentWatchlistItemPo item) {
        return new InvestmentWatchlistItemResponse(
                item.getId(), item.getInstrumentId(), item.getSortNo(), item.getNote(), item.getCreatedAt());
    }

    private static String normalizedName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 128) {
            throw new InvestmentException(InvestmentErrorCode.INVALID_REQUEST, "Watchlist name is required");
        }
        return name.trim();
    }

    private static String normalizedText(String value, int maxLength, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new InvestmentException(
                    InvestmentErrorCode.INVALID_REQUEST, fieldName + " exceeds maximum length");
        }
        return normalized;
    }

    private static boolean causedByConstraint(Throwable error, String constraintName) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().toLowerCase(java.util.Locale.ROOT)
                    .contains(constraintName.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
