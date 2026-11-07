package com.todo.user.controller;

import com.todo.user.dto.UserResponse;
import com.todo.user.entity.User;
import com.todo.user.exception.UserProfileForbiddenException;
import com.todo.user.security.CurrentUser;
import com.todo.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {
    @Mock private UserService userService;
    @Mock private CurrentUser currentUser;
    @InjectMocks private UserController controller;

    @Test
    void ownerCanReadOwnProfile() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).username("owner").build();
        UserResponse profile = UserResponse.builder().id(ownerId).username("owner").build();

        when(currentUser.getOrThrow()).thenReturn(owner);
        when(userService.getById(ownerId)).thenReturn(profile);

        var response = controller.getById(ownerId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(ownerId, response.getBody().getId());
        verify(userService).getById(ownerId);
    }

    @Test
    void authenticatedUserCannotReadSomeoneElsesProfile() {
        User caller = User.builder().id(UUID.randomUUID()).username("caller").build();
        UUID otherId = UUID.randomUUID();
        when(currentUser.getOrThrow()).thenReturn(caller);

        assertThrows(UserProfileForbiddenException.class, () -> controller.getById(otherId));

        verifyNoInteractions(userService);
    }

    @Test
    void ownerCanCheckOwnExistence() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).username("owner").build();

        when(currentUser.getOrThrow()).thenReturn(owner);
        when(userService.existsById(ownerId)).thenReturn(true);

        var response = controller.existsById(ownerId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody());
        verify(userService).existsById(ownerId);
    }

    @Test
    void authenticatedUserCannotCheckAnotherAccountsExistence() {
        User caller = User.builder().id(UUID.randomUUID()).username("caller").build();
        UUID otherId = UUID.randomUUID();
        when(currentUser.getOrThrow()).thenReturn(caller);

        assertThrows(UserProfileForbiddenException.class, () -> controller.existsById(otherId));
        verifyNoInteractions(userService);
    }
}
