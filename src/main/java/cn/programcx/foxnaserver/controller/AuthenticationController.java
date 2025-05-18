package cn.programcx.foxnaserver.controller;

import cn.programcx.foxnaserver.service.AuthenticationService;
import cn.programcx.foxnaserver.util.JwtUtil;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(("/api/auth"))
public class AuthenticationController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;


    @Autowired
    private AuthenticationService authenticationService;

    @PostMapping("/login")
    private ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {

        try{
            authenticationService.checkUserStatus(loginRequest.getUsername());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            //生成token
            String token = jwtUtil.generateToken(authentication);

            return ResponseEntity.ok(token);
        }
        catch (UsernameNotFoundException e) {
            System.out.println("Username not found: " + e.getMessage());
            return ResponseEntity.status(401).body("Invalid username or password");
        } catch (BadCredentialsException e) {
            System.out.println("Invalid credentials: " + e.getMessage());
            return ResponseEntity.status(401).body("Invalid username or password");
        } catch (Exception e) {
            System.out.println("Login error: " + e.getMessage());
            return ResponseEntity.status(401).body("Login failed"+e.getMessage());
        }


    }

    @PostMapping("iniAdmin")
    private ResponseEntity<?> iniAdmin(){
        if(authenticationService.registerAdmin()){
            return ResponseEntity.status(200).body("123456");
        }else{
            return ResponseEntity.status(HttpStatus.CONFLICT).body("admin already registered");
        }
    }


}

@Data
class LoginRequest {
    private String username;
    private String password;
}

