package top.egon.mario.nutrition.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.nutrition.po.NutritionHealthTagPo;
import top.egon.mario.nutrition.po.enums.NutritionStatus;

import java.util.List;
import java.util.Optional;

public interface NutritionHealthTagRepository extends JpaRepository<NutritionHealthTagPo, Long> {

    Optional<NutritionHealthTagPo> findByIdAndDeletedFalse(Long id);

    Optional<NutritionHealthTagPo> findByTagTypeIgnoreCaseAndTagCodeIgnoreCaseAndDeletedFalse(
            String tagType, String tagCode);

    List<NutritionHealthTagPo> findByStatusAndDeletedFalseOrderByTagTypeAscSortOrderAscIdAsc(
            NutritionStatus status);

    List<NutritionHealthTagPo> findByDeletedFalseOrderByTagTypeAscSortOrderAscIdAsc();

    List<NutritionHealthTagPo> findByTagTypeIgnoreCaseAndStatusAndDeletedFalseOrderBySortOrderAscIdAsc(
            String tagType, NutritionStatus status);

    List<NutritionHealthTagPo> findByTagTypeIgnoreCaseAndDeletedFalseOrderBySortOrderAscIdAsc(String tagType);

    boolean existsByTagTypeIgnoreCaseAndTagCodeIgnoreCaseAndIdNotAndDeletedFalse(
            String tagType, String tagCode, Long id);
}
