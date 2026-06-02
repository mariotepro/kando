package com.kando.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class LevenshteinUtilTest {

    @Test
    void sameString_returnsZero() {
        assertThat(LevenshteinUtil.distance("hello", "hello")).isZero();
    }

    @Test
    void emptyStrings_returnsZero() {
        assertThat(LevenshteinUtil.distance("", "")).isZero();
    }

    @Test
    void oneEmpty_returnsLengthOfOther() {
        assertThat(LevenshteinUtil.distance("abc", "")).isEqualTo(3);
        assertThat(LevenshteinUtil.distance("", "abc")).isEqualTo(3);
    }

    @ParameterizedTest(name = "''{0}'' vs ''{1}'' = {2}")
    @CsvSource({
        "kitten,  sitting, 3",
        "saturday, sunday,  3",
        "abc,      ab,      1",
        "abc,      abcd,    1",
        "abc,      xyz,     3"
    })
    void knownDistances(String a, String b, int expected) {
        assertThat(LevenshteinUtil.distance(a.trim(), b.trim())).isEqualTo(expected);
    }

    @Test
    void caseInsensitive() {
        assertThat(LevenshteinUtil.distance("ABC", "abc")).isZero();
    }
}
