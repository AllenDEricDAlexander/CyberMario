package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImGroupPo;

import java.util.Optional;

public interface ImGroupRepository extends JpaRepository<ImGroupPo, Long> {

    Optional<ImGroupPo> findByIdAndDeletedFalse(Long id);

    Optional<ImGroupPo> findByChannelIdAndGroupKeyAndDeletedFalse(Long channelId, String groupKey);
}
