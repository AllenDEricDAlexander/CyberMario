package top.egon.mario.im.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.im.po.ImChannelPo;

import java.util.Optional;

public interface ImChannelRepository extends JpaRepository<ImChannelPo, Long> {

    Optional<ImChannelPo> findByIdAndDeletedFalse(Long id);

    Optional<ImChannelPo> findByContextTypeAndContextIdAndChannelKeyAndDeletedFalse(
            String contextType, Long contextId, String channelKey);
}
