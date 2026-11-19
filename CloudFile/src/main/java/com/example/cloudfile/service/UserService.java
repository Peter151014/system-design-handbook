package com.example.cloudfile.service;

import com.example.cloudfile.dto.Response;
import com.example.cloudfile.dto.createUser;
import com.example.cloudfile.entity.Users;
import com.example.cloudfile.exception.UserAlreadyExists;
import com.example.cloudfile.repository.UserRepo;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepo userRepo;

    public UserService(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    public Response createUser(createUser user){
        if(userRepo.existsByEmail(user.getEmail())) {
            throw new UserAlreadyExists("User already exists");
        }

        Users user2 = new Users();
        user2.setName(user.getName());
        user2.setEmail(user.getEmail());
        user2.setPhoneNumber(user.getPhoneNumber());

        userRepo.save(user2);

        return new Response("User Successfully Registered ");
    }

}
