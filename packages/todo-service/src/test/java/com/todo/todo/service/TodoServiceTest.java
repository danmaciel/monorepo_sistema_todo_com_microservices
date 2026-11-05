package com.todo.todo.service;

import com.todo.todo.dto.CreateTodoRequest;
import com.todo.todo.dto.UpdateTodoRequest;
import com.todo.todo.entity.Todo;
import com.todo.todo.exception.TodoNotFoundException;
import com.todo.todo.exception.UnauthorizedException;
import com.todo.todo.repository.TodoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TodoServiceTest {
    @Mock private TodoRepository repository;
    @InjectMocks private TodoService service;

    @Test
    void createAssignsAuthenticatedOwner() {
        UUID owner = UUID.randomUUID();
        CreateTodoRequest request = new CreateTodoRequest("Revisar arquitetura", "Criar ADR");
        when(repository.save(any(Todo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(request, owner);

        assertEquals(owner, result.getUserId());
        assertEquals("Revisar arquitetura", result.getTitle());
        assertFalse(result.getCompleted());
        verify(repository).save(argThat(todo -> owner.equals(todo.getUserId())));
    }

    @Test
    void readByDifferentUserIsForbidden() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID todoId = UUID.randomUUID();
        when(repository.findById(todoId)).thenReturn(Optional.of(Todo.builder()
                .id(todoId).title("Privado").userId(owner).completed(false).build()));

        assertThrows(UnauthorizedException.class, () -> service.getById(todoId, other));
        verify(repository, never()).save(any());
    }

    @Test
    void updateByDifferentUserDoesNotModifyOrSave() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Todo existing = Todo.builder().id(id).title("Original").userId(owner).completed(false).build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        assertThrows(UnauthorizedException.class, () ->
                service.update(id, new UpdateTodoRequest("Alterado", null, true), other));

        assertEquals("Original", existing.getTitle());
        verify(repository, never()).save(any());
    }

    @Test
    void deletionByDifferentUserIsForbidden() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(Todo.builder()
                .id(id).title("Privado").userId(owner).completed(false).build()));

        assertThrows(UnauthorizedException.class, () -> service.delete(id, other));
        verify(repository, never()).delete(any());
    }

    @Test
    void missingTodoReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(TodoNotFoundException.class, () -> service.getById(id, UUID.randomUUID()));
    }

    @Test
    void ownerCanCompleteTodo() {
        UUID owner = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Todo todo = Todo.builder().id(id).title("Tarefa").userId(owner).completed(false).build();
        when(repository.findById(id)).thenReturn(Optional.of(todo));
        when(repository.save(todo)).thenReturn(todo);

        var result = service.markAsComplete(id, owner);

        assertTrue(result.getCompleted());
        verify(repository).save(todo);
    }
}
