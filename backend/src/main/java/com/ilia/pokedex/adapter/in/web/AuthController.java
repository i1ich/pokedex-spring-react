package com.ilia.pokedex.adapter.in.web;

import com.ilia.pokedex.adapter.in.web.dto.CredentialsRequest;
import com.ilia.pokedex.adapter.in.web.dto.LoginResponse;
import com.ilia.pokedex.adapter.in.web.dto.UserResponse;
import com.ilia.pokedex.application.port.in.AuthenticateUserUseCase;
import com.ilia.pokedex.application.port.in.RegisterUserUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login and the current user. Register and login are public; {@code /me} needs a valid token
 * and is answered from the token itself (subject and roles), without a database call.
 */
@RestController
@RequestMapping(AuthController.BASE_PATH)
public class AuthController {

    static final String BASE_PATH = "/api/v1/auth";
    private static final String ROLE_PREFIX = "ROLE_";

    private final RegisterUserUseCase registerUser;
    private final AuthenticateUserUseCase authenticateUser;

    public AuthController(RegisterUserUseCase registerUser, AuthenticateUserUseCase authenticateUser) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody CredentialsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(registerUser.register(request.username(), request.password())));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody CredentialsRequest request) {
        return LoginResponse.from(authenticateUser.authenticate(request.username(), request.password()));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .findFirst()
                .orElse(null);
        return new UserResponse(authentication.getName(), role);
    }
}
