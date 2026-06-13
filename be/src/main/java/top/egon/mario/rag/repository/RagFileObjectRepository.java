package top.egon.mario.rag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rag.po.RagFileObjectPo;

import java.util.Optional;

/**
 * Repository for globally de-duplicated RAG file objects.
 */
public interface RagFileObjectRepository extends JpaRepository<RagFileObjectPo, Long> {

    Optional<RagFileObjectPo> findByIdAndDeletedFalse(Long id);

    Optional<RagFileObjectPo> findBySha256AndDeletedFalse(String sha256);

}
