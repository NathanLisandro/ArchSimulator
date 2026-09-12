package dev.archsim.application.usecase;

import dev.archsim.application.dto.UserView;
import dev.archsim.application.fakes.InMemory;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserUseCasesTest {
    private final InMemory.Users users = new InMemory.Users();
    private final InMemory.PlainHasher hasher = new InMemory.PlainHasher();
    private final RegisterUser register = new RegisterUser(users, hasher, new InMemory.FixedClock(Instant.EPOCH));
    private final AuthenticateUser authenticate = new AuthenticateUser(users, hasher);

    @Test
    void registersAndAuthenticates() {
        UserView u = register.execute(new RegisterUser.Command("Nathan", "Nathan L.", "secret1"));
        assertThat(u.username()).isEqualTo("nathan");
        assertThat(authenticate.execute("nathan", "secret1")).isPresent();
        assertThat(authenticate.execute("nathan", "wrong")).isEmpty();
        assertThatThrownBy(() -> register.execute(new RegisterUser.Command("nathan", "x", "secret1"))).hasMessageContaining("já existe");
    }
}
