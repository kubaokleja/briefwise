package com.briefwise.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccessFacadeIT extends AbstractIntegrationTest {

    @Autowired
    AccessFacade facade;

    @Test
    void facade_is_wired() {
        assertThat(facade).isNotNull();
    }
}
