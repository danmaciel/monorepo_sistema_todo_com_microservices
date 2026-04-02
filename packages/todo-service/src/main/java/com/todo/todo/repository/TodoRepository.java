package com.todo.todo.repository;

import com.todo.todo.entity.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TodoRepository extends JpaRepository<Todo, UUID> {
    
    List<Todo> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    List<Todo> findByUserIdAndCompletedOrderByCreatedAtDesc(UUID userId, Boolean completed);
    
    long countByUserId(UUID userId);
    
    long countByUserIdAndCompleted(UUID userId, Boolean completed);
    
    boolean existsByIdAndUserId(UUID id, UUID userId);
    
    void deleteByIdAndUserId(UUID id, UUID userId);
}