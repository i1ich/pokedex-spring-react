package com.example.tasks.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.tasks.domain.Task;
import com.example.tasks.domain.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdAndOwnerUsername(Long id, String ownerUsername);

    @Query(value = "select t from Task t where t.ownerUsername = :owner "
            + "order by t.dueDate asc nulls last, t.id asc",
            countQuery = "select count(t) from Task t where t.ownerUsername = :owner")
    Page<Task> searchByOwner(@Param("owner") String owner, Pageable pageable);

    @Query(value = "select t from Task t where t.ownerUsername = :owner and t.status = :status "
            + "order by t.dueDate asc nulls last, t.id asc",
            countQuery = "select count(t) from Task t where t.ownerUsername = :owner and t.status = :status")
    Page<Task> searchByOwnerAndStatus(@Param("owner") String owner,
                                      @Param("status") TaskStatus status,
                                      Pageable pageable);
}
