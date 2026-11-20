package interview.guide.modules.auth.repository;

import interview.guide.modules.auth.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository  extends JpaRepository<UserEntity,Long>{
    public Optional<UserEntity> findByUsername(String username);
    public boolean existsByUsername(String username);
}