package io.github.nemios11.cvgenerator.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    void keepsTheIssuedValue() {
        assertThat(new UserId("3f1c-42").value()).isEqualTo("3f1c-42");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new UserId("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongValues() {
        assertThatThrownBy(() -> new UserId("x".repeat(256))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalityIsByValue() {
        assertThat(new UserId("a")).isEqualTo(new UserId("a")).isNotEqualTo(new UserId("b"));
    }
}
