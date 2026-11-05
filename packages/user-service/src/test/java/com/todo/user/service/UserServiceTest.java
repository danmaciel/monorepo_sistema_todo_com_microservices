package com.todo.user.service;

import com.todo.user.dto.LoginRequest;
import com.todo.user.dto.RegisterRequest;
import com.todo.user.entity.User;
import com.todo.user.exception.InvalidCredentialsException;
import com.todo.user.exception.UsernameAlreadyExistsException;
import com.todo.user.repository.UserRepository;
import com.todo.user.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository repository;
    @Mock PasswordEncoder encoder;
    @Mock JwtTokenProvider tokens;
    @InjectMocks UserService service;

    @Test
    void registerRejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest("teste", "teste@example.com", "password");
        when(repository.existsByUsername("teste")).thenReturn(true);

        assertThrows(UsernameAlreadyExistsException.class, () -> service.register(request));
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder, tokens);
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = User.builder().id(UUID.randomUUID()).username("teste")
                .password("storedHash").enabled(true).build();
        when(repository.findByUsername("teste")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "storedHash")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () ->
                service.login(new LoginRequest("teste", "wrong")));
        verifyNoInteractions(tokens);
    }

    @Test
    void disabledUserCannotLogin() {
        User user = User.builder().id(UUID.randomUUID()).username("teste")
                .password("storedHash").enabled(false).build();
        when(repository.findByUsername("teste")).thenReturn(Optional.of(user));
        when(encoder.matches("password", "storedHash")).thenReturn(true);

        assertThrows(InvalidCredentialsException.class, () ->
                service.login(new LoginRequest("teste", "password")));
        verifyNoInteractions(tokens);
    }

    @Test
    void validLoginGeneratesToken() {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).username("teste").email("teste@example.com")
                .password("storedHash").enabled(true).build();
        when(repository.findByUsername("teste")).thenReturn(Optional.of(user));
        when(encoder.matches("password", "storedHash")).thenReturn(true);
        when(tokens.generateToken(id, "teste")).thenReturn("generated-token");

        var response = service.login(new LoginRequest("teste", "password"));

        assertEquals("generated-token", response.getToken());
        assertEquals("Bearer", response.getType());
        assertEquals(id, response.getUser().getId());
    }
}
