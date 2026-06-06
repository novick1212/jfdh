package com.novick.points.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.novick.points.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByPhoneNumber(String phoneNumber);

    Optional<UserAccount> findByHrCode(String hrCode);
}
