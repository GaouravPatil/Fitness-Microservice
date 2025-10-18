package com.fitness.userservice.services;

import com.fitness.userservice.Models.User;
import com.fitness.userservice.UserRepo;
import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class UserService {

    private UserRepo repository;
    public UserResponse register (RegisterRequest request){

        if (repository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exist");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setF_name(request.getF_name());
        user.setL_name(request.getL_name());
        user.setPassword(request.getPassword());


        User  savedUser = repository.save(user);
        UserResponse res = new UserResponse();
        res.setId(savedUser.getId());
        res.setPassword(savedUser.getPassword());
        res.setEmail(savedUser.getEmail());
        res.setF_name(savedUser.getF_name());
        res.setL_name(savedUser.getL_name());
        res.setCreatedAt(savedUser.getCreatedAt());
        res.setUpdatedAt(savedUser.getUpdatedAt());

        return res;


    }

    public UserResponse getUserProfile(String userId) {
        User user = repository.findById((userId))
                .orElseThrow(() -> new RuntimeException("User not found"));
        UserResponse res = new UserResponse();
        res.setId(user.getId());
        res.setPassword(user.getPassword());
        res.setEmail(user.getEmail());
        res.setF_name(user.getF_name());
        res.setL_name(user.getL_name());
        res.setCreatedAt(user.getCreatedAt());
        res.setUpdatedAt(user.getUpdatedAt());
        return res;
    }
}
