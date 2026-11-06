package com.todo.user.security;

import com.todo.user.entity.User;
import com.todo.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock private JwtTokenProvider tokens;
    @Mock private UserRepository repository;
    @InjectMocks private JwtAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/users/me");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void acceptsValidTokenForEnabledAccount() throws Exception {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).enabled(true).build();
        when(tokens.validateToken("valid")).thenReturn(true);
        when(tokens.getUserIdFromToken("valid")).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(user));

        authenticate("valid");

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @Test
    void rejectsValidTokenForDisabledAccount() throws Exception {
        UUID id = UUID.randomUUID();
        when(tokens.validateToken("valid")).thenReturn(true);
        when(tokens.getUserIdFromToken("valid")).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.of(User.builder().id(id).enabled(false).build()));

        authenticate("valid");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rejectsValidTokenForDeletedAccount() throws Exception {
        UUID id = UUID.randomUUID();
        when(tokens.validateToken("valid")).thenReturn(true);
        when(tokens.getUserIdFromToken("valid")).thenReturn(id);
        when(repository.findById(id)).thenReturn(Optional.empty());

        authenticate("valid");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void invalidTokenDoesNotLookupAccount() throws Exception {
        when(tokens.validateToken("invalid")).thenReturn(false);

        authenticate("invalid");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(repository);
    }
}
