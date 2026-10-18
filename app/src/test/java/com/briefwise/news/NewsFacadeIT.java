package com.briefwise.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.briefwise.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NewsFacadeIT extends AbstractIntegrationTest {

    @Autowired
    NewsFacade facade;

    @Test
    void facade_is_wired() {
        assertThat(facade).isNotNull();
    }
}
