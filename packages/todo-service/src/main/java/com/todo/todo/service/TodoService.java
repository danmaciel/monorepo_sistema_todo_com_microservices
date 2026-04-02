package com.todo.todo.service;

import com.todo.todo.dto.*;
import com.todo.todo.entity.Todo;
import com.todo.todo.exception.TodoNotFoundException;
import com.todo.todo.exception.UnauthorizedException;
import com.todo.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    @Transactional
    public TodoResponse create(CreateTodoRequest request, UUID userId) {
        Todo todo = Todo.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .completed(false)
                .userId(userId)
                .build();

        todo = todoRepository.save(todo);
        return toResponse(todo);
    }

    public TodoListResponse getAll(UUID userId, Boolean filterCompleted) {
        List<Todo> todos;
        
        if (filterCompleted != null) {
            todos = todoRepository.findByUserIdAndCompletedOrderByCreatedAtDesc(userId, filterCompleted);
        } else {
            todos = todoRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        long total = todoRepository.countByUserId(userId);
        long active = todoRepository.countByUserIdAndCompleted(userId, false);
        long completed = todoRepository.countByUserIdAndCompleted(userId, true);

        return TodoListResponse.builder()
                .todos(todos.stream().map(this::toResponse).collect(Collectors.toList()))
                .total((int) total)
                .active((int) active)
                .completed((int) completed)
                .build();
    }

    public TodoResponse getById(UUID id, UUID userId) {
        Todo todo = findTodoByIdAndValidateUser(id, userId);
        return toResponse(todo);
    }

    @Transactional
    public TodoResponse update(UUID id, UpdateTodoRequest request, UUID userId) {
        Todo todo = findTodoByIdAndValidateUser(id, userId);

        if (request.getTitle() != null) {
            todo.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            todo.setDescription(request.getDescription());
        }
        if (request.getCompleted() != null) {
            todo.setCompleted(request.getCompleted());
        }

        todo = todoRepository.save(todo);
        return toResponse(todo);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Todo todo = findTodoByIdAndValidateUser(id, userId);
        todoRepository.delete(todo);
    }

    @Transactional
    public TodoResponse markAsComplete(UUID id, UUID userId) {
        Todo todo = findTodoByIdAndValidateUser(id, userId);
        todo.setCompleted(true);
        todo = todoRepository.save(todo);
        return toResponse(todo);
    }

    @Transactional
    public TodoResponse markAsIncomplete(UUID id, UUID userId) {
        Todo todo = findTodoByIdAndValidateUser(id, userId);
        todo.setCompleted(false);
        todo = todoRepository.save(todo);
        return toResponse(todo);
    }

    private Todo findTodoByIdAndValidateUser(UUID id, UUID userId) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException("Todo não encontrado com ID: " + id));

        if (!todo.getUserId().equals(userId)) {
            throw new UnauthorizedException("Você não tem permissão para acessar este todo");
        }

        return todo;
    }

    private TodoResponse toResponse(Todo todo) {
        return TodoResponse.builder()
                .id(todo.getId())
                .title(todo.getTitle())
                .description(todo.getDescription())
                .completed(todo.getCompleted())
                .userId(todo.getUserId())
                .createdAt(todo.getCreatedAt())
                .updatedAt(todo.getUpdatedAt())
                .build();
    }
}