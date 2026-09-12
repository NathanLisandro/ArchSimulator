package dev.archsim.infrastructure.web;

import dev.archsim.application.dto.UserView;
import dev.archsim.application.usecase.AuthenticateUser;
import dev.archsim.application.usecase.RegisterUser;
import dev.archsim.domain.user.UserId;
import dev.archsim.domain.user.UserRepository;
import dev.archsim.infrastructure.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    public record LoginRequest(String username, String password) {}
    public record RegisterRequest(String username, String displayName, String password) {}

    private final RegisterUser registerUser;
    private final AuthenticateUser authenticateUser;
    private final UserRepository users;
    private final SecurityContextRepository contextRepository;

    public AuthController(RegisterUser registerUser, AuthenticateUser authenticateUser, UserRepository users, SecurityContextRepository contextRepository) {
        this.registerUser = registerUser; this.authenticateUser = authenticateUser; this.users = users; this.contextRepository = contextRepository;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@RequestBody RegisterRequest req) {
        return registerUser.execute(new RegisterUser.Command(req.username(), req.displayName(), req.password()));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        return authenticateUser.execute(req.username(), req.password()).<ResponseEntity<?>>map(user -> {
            var auth = new UsernamePasswordAuthenticationToken(user.id(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
            contextRepository.saveContext(ctx, request, response);
            return ResponseEntity.ok(user);
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Usuário ou senha inválidos")));
    }

    @PostMapping("/auth/logout")
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    @GetMapping("/auth/me")
    public UserView me() {
        UserId id = CurrentUser.id();
        return users.findById(id).map(UserView::from).orElseThrow(() -> new IllegalStateException("Usuário não encontrado"));
    }
}
