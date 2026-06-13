package top.egon.mario.rbac.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.egon.mario.rbac.po.RefreshTokenPo;

import java.util.Optional;

/**
 * Repository for refresh token rotation state.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenPo, Long> {

    Optional<RefreshTokenPo> findByTokenId(String tokenId);

}
