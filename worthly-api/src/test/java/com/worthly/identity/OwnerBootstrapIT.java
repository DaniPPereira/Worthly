package com.worthly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.worthly.AbstractIntegrationTest;
import com.worthly.identity.adapter.out.persistence.AppUserRepository;
import com.worthly.identity.application.OwnerBootstrapRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;

class OwnerBootstrapIT extends AbstractIntegrationTest {

    @Autowired
    AppUserRepository users;

    @Autowired
    OwnerBootstrapRunner bootstrapRunner;

    @Test
    void bootstrapsExactlyOneOwnerAndIgnoresSecondRun() {
        assertThat(users.count()).isEqualTo(1);
        assertThat(users.findByEmailIgnoreCase(OWNER_EMAIL)).isPresent();
        bootstrapRunner.run(new DefaultApplicationArguments());
        assertThat(users.count()).isEqualTo(1);
    }
}
