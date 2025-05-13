package cn.programcx.foxnaserver.controller;

import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller("/auth")
public class AuthenticationController {

    @PostMapping("login")
    private ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        return null;
    }

}

@Data
class LoginRequest {
    private String username;
    private String password;
}

