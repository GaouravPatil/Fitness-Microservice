package com.fitness.userservice.repository;

import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsUsersByEmailIgnoringCase() {
        userRepository.saveAndFlush(user("Jane@Example.com"));

        Optional<User> found = userRepository.findByEmailIgnoreCase("jane@example.com");

        assertThat(found).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase("JANE@EXAMPLE.COM")).isTrue();
    }

    @Test
    void rejectsDuplicateEmailAtDatabaseLevel() {
        userRepository.saveAndFlush(user("jane@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("jane@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String email) {
        return User.builder()
                .email(email)
                .password("hash")
                .firstName("Jane")
                .lastName("Doe")
                .role(UserRole.USER)
                .build();
    }
}
