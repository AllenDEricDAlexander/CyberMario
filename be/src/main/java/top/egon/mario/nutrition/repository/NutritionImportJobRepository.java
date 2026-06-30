package top.egon.mario.nutrition.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import top.egon.mario.nutrition.po.NutritionImportJobPo;

import java.util.Optional;

public interface NutritionImportJobRepository extends JpaRepository<NutritionImportJobPo, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select importJob from NutritionImportJobPo importJob where importJob.id = :id")
    Optional<NutritionImportJobPo> findLockedById(@Param("id") Long id);
}
