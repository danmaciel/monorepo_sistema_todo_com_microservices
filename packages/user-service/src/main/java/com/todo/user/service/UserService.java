package com.todo.user.service;

import com.todo.user.dto.*;
import com.todo.user.entity.User;
import com.todo.user.exception.*;
import com.todo.user.repository.UserRepository;
import com.todo.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validar se username já existe
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException("Username '" + request.getUsername() + "' já está em uso");
        }

        // Validar se email já existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email '" + request.getEmail() + "' já está em uso");
        }

        // Criar usuário
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .enabled(true)
                .build();

        user = userRepository.save(user);

        // Gerar token
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return AuthResponse.of(token, toResponse(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Username ou senha incorretos"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Username ou senha incorretos");
        }

        if (!user.getEnabled()) {
            throw new InvalidCredentialsException("Usuário desativado");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return AuthResponse.of(token, toResponse(user));
    }

    public UserResponse getById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuário não encontrado com ID: " + id));

        return toResponse(user);
    }

    public UserResponse getCurrentUser(User user) {
        return toResponse(user);
    }

    public boolean existsById(UUID id) {
        return userRepository.existsById(id);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }
}