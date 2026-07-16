package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import top.egon.mario.im.po.ImContactPo;
import top.egon.mario.im.po.enums.ImContactStatus;

import java.util.List;
import java.util.Optional;

public interface ImContactRepository extends JpaRepository<ImContactPo, Long> {

    Optional<ImContactPo> findByIdAndDeletedFalse(Long id);

    Optional<ImContactPo> findByOwnerUserIdAndContactUserIdAndDeletedFalse(Long ownerUserId, Long contactUserId);

    List<ImContactPo> findByFriendshipIdAndDeletedFalse(Long friendshipId);

    Page<ImContactPo> findByOwnerUserIdAndStatusAndDeletedFalse(
            Long ownerUserId, ImContactStatus status, Pageable pageable);
}
