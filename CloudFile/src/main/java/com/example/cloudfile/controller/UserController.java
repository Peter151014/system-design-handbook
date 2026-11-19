package com.example.cloudfile.controller;

import com.example.cloudfile.dto.Response;
import com.example.cloudfile.dto.createUser;
import com.example.cloudfile.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;


    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/createUser")
    public Response createUser(@RequestBody createUser user){
        return userService.createUser(user);
    }

}
