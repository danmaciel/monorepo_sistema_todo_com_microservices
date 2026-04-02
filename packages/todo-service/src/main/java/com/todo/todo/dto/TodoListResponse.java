package com.todo.todo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoListResponse {
    private List<TodoResponse> todos;
    private int total;
    private int active;
    private int completed;
}