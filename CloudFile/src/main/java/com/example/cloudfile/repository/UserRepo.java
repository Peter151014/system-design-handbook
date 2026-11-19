package com.example.cloudfile.repository;

import com.example.cloudfile.entity.Users;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepo extends JpaRepository<Users, Long> {
    Boolean findByEmail(@NotBlank String email);

    Boolean existsByEmail(String email);
}
