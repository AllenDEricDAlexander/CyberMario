package top.egon.mario.clocktower.game.nomination.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.clocktower.game.nomination.po.ClocktowerGameVotePo;

import java.util.List;
import java.util.Optional;

public interface ClocktowerGameVoteRepository extends JpaRepository<ClocktowerGameVotePo, Long> {

    Optional<ClocktowerGameVotePo> findByNominationIdAndVoterGameSeatIdAndDeletedFalse(
            Long nominationId, Long voterGameSeatId);

    boolean existsByNominationIdAndVoterGameSeatIdAndDeletedFalse(Long nominationId, Long voterGameSeatId);

    long countByNominationIdAndVoteValueTrueAndDeletedFalse(Long nominationId);

    List<ClocktowerGameVotePo> findByNominationIdAndDeletedFalseOrderByIdAsc(Long nominationId);
}
