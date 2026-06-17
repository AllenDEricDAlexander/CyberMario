package top.egon.mario.clocktower.grimoire.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.grimoire.po.ClocktowerVotePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerVoteRepository extends JpaRepository<ClocktowerVotePo, Long> {

    List<ClocktowerVotePo> findByRoomIdAndDeletedFalseOrderByIdAsc(Long roomId);

    List<ClocktowerVotePo> findByNominationIdAndDeletedFalseOrderByIdAsc(Long nominationId);

    Optional<ClocktowerVotePo> findByNominationIdAndVoterSeatIdAndDeletedFalse(Long nominationId, Long voterSeatId);
}
