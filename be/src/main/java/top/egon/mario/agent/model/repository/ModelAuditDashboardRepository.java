package top.egon.mario.agent.model.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import top.egon.mario.agent.model.dto.request.ModelAuditDashboardQuery;
import top.egon.mario.agent.model.dto.response.ModelAuditUserOptionResponse;
import top.egon.mario.agent.model.po.ModelAuditPo;
import top.egon.mario.rbac.po.UserPo;
import top.egon.mario.rbac.repository.UserRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Executes read-only model audit dashboard queries.
 */
@Repository
@RequiredArgsConstructor
public class ModelAuditDashboardRepository {

    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public List<ModelAuditPo> findAudits(ModelAuditDashboardQuery query, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ModelAuditPo> cq = cb.createQuery(ModelAuditPo.class);
        Root<ModelAuditPo> root = cq.from(ModelAuditPo.class);
        cq.select(root)
                .where(predicates(cb, root, query).toArray(new Predicate[0]))
                .orderBy(cb.desc(root.get("createdAt")));
        return entityManager.createQuery(cq)
                .setMaxResults(limit)
                .getResultList();
    }

    public Page<ModelAuditPo> recentCalls(ModelAuditDashboardQuery query, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<ModelAuditPo> cq = cb.createQuery(ModelAuditPo.class);
        Root<ModelAuditPo> root = cq.from(ModelAuditPo.class);
        cq.select(root)
                .where(predicates(cb, root, query).toArray(new Predicate[0]))
                .orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
        List<ModelAuditPo> content = entityManager.createQuery(cq)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(content, pageable, countAudits(query));
    }

    private long countAudits(ModelAuditDashboardQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<ModelAuditPo> root = cq.from(ModelAuditPo.class);
        cq.select(cb.count(root))
                .where(predicates(cb, root, query).toArray(new Predicate[0]));
        return entityManager.createQuery(cq).getSingleResult();
    }

    public List<DimensionRow> dimensionStats(ModelAuditDashboardQuery query, String fieldName, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<ModelAuditPo> root = cq.from(ModelAuditPo.class);
        cq.multiselect(
                        root.get(fieldName),
                        cb.count(root),
                        cb.coalesce(cb.sum(root.get("totalTokens")), 0L),
                        cb.coalesce(cb.avg(root.get("durationMs")), 0D)
                )
                .where(predicates(cb, root, query).toArray(new Predicate[0]))
                .groupBy(root.get(fieldName))
                .orderBy(cb.desc(cb.coalesce(cb.sum(root.get("totalTokens")), 0L)), cb.desc(cb.count(root)));
        return entityManager.createQuery(cq)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(row -> new DimensionRow(
                        row[0] == null ? null : row[0].toString(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).doubleValue()))
                .toList();
    }

    public List<UserStatRow> userStats(ModelAuditDashboardQuery query, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<UserStatRow> cq = cb.createQuery(UserStatRow.class);
        Root<ModelAuditPo> root = cq.from(ModelAuditPo.class);
        cq.select(cb.construct(
                        UserStatRow.class,
                        root.get("userId"),
                        cb.count(root),
                        cb.coalesce(cb.sum(root.get("totalTokens")), 0L)
                ))
                .where(predicates(cb, root, query).toArray(new Predicate[0]))
                .groupBy(root.get("userId"))
                .orderBy(cb.desc(cb.coalesce(cb.sum(root.get("totalTokens")), 0L)), cb.desc(cb.count(root)));
        return entityManager.createQuery(cq)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<UserPo> users(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findByIdInAndDeletedFalse(userIds);
    }

    public List<ModelAuditUserOptionResponse> userOptions(String keyword, int size) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (StringUtils.hasText(trimmed) && trimmed.chars().allMatch(Character::isDigit)) {
            return userRepository.findByIdAndDeletedFalse(Long.valueOf(trimmed))
                    .map(user -> List.of(new ModelAuditUserOptionResponse(user.getId(), user.getUsername(), user.getNickname())))
                    .orElseGet(List::of);
        }
        return userRepository.findAll((root, query, cb) -> {
                    List<Predicate> conditions = new ArrayList<>();
                    conditions.add(cb.isFalse(root.get("deleted")));
                    if (StringUtils.hasText(trimmed)) {
                        String pattern = "%" + trimmed.toLowerCase() + "%";
                        conditions.add(cb.or(
                                cb.like(cb.lower(root.get("username")), pattern),
                                cb.like(cb.lower(root.get("nickname")), pattern)
                        ));
                    }
                    return cb.and(conditions.toArray(new Predicate[0]));
                }, PageRequest.of(0, Math.max(1, size), Sort.by("id").ascending()))
                .map(user -> new ModelAuditUserOptionResponse(user.getId(), user.getUsername(), user.getNickname()))
                .getContent();
    }

    private List<Predicate> predicates(CriteriaBuilder cb, Root<ModelAuditPo> root, ModelAuditDashboardQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), query.startAt()));
        predicates.add(cb.lessThan(root.get("createdAt"), query.endAt()));
        if (query.userId() != null) {
            predicates.add(cb.equal(root.get("userId"), query.userId()));
        }
        if (query.provider() != null) {
            predicates.add(cb.equal(root.get("provider"), query.provider()));
        }
        if (StringUtils.hasText(query.model())) {
            predicates.add(cb.equal(root.get("model"), query.model().trim()));
        }
        if (query.scenario() != null) {
            predicates.add(cb.equal(root.get("scenario"), query.scenario()));
        }
        if (query.status() != null) {
            predicates.add(cb.equal(root.get("status"), query.status()));
        }
        return predicates;
    }

    public record DimensionRow(String name, long callCount, long totalTokens, double avgDurationMs) {
    }

    public record UserStatRow(Long userId, long callCount, long totalTokens) {
    }

}
