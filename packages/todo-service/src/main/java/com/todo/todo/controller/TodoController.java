package com.todo.todo.controller;

import com.todo.todo.dto.*;
import com.todo.todo.security.AuthenticatedUser;
import com.todo.todo.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @PostMapping
    public ResponseEntity<TodoResponse> create(
            @Valid @RequestBody CreateTodoRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        TodoResponse response = todoService.create(request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<TodoListResponse> getAll(
            @RequestParam(required = false) Boolean completed,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(todoService.getAll(user.getUserId(), completed));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TodoResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(todoService.getById(id, user.getUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TodoResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTodoRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(todoService.update(id, request, user.getUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        todoService.delete(id, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TodoResponse> markAsComplete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(todoService.markAsComplete(id, user.getUserId()));
    }

    @PatchMapping("/{id}/uncomplete")
    public ResponseEntity<TodoResponse> markAsIncomplete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(todoService.markAsIncomplete(id, user.getUserId()));
    }
}