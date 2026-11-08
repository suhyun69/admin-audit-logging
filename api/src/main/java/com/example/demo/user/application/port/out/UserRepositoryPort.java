package com.example.demo.user.application.port.out;
import com.example.demo.user.domain.*;
import java.util.List;
import java.util.Optional;
public interface UserRepositoryPort {
    List<User> find(String search,UserStatus status,int limit,Long afterId);
    Optional<User> findById(long id);
}
