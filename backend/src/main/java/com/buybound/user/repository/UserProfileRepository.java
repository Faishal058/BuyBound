package com.buybound.user.repository;

import com.buybound.user.domain.UserProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
  Optional<UserProfile> findFirstByLoggedInTrueOrderByUpdatedAtDesc();

  List<UserProfile> findByLoggedInTrue();

  Optional<UserProfile> findByEmailIgnoreCase(String email);

  Optional<UserProfile> findFirstByPaypalEmailIgnoreCaseOrderByUpdatedAtDesc(String paypalEmail);

  Optional<UserProfile> findFirstByPaypalPayerIdOrderByUpdatedAtDesc(String paypalPayerId);

  Optional<UserProfile> findFirstByOrderByCreatedAtAsc();
}

