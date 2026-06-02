package com.kando.service;

import com.kando.repository.KandoUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

    @Mock DataSource          dataSource;
    @Mock KandoUserRepository userRepository;

    @InjectMocks
    SetupService setupService;

    @Test
    void needsAdminSetup_whenNoUsers_returnsTrue() {
        when(userRepository.count()).thenReturn(0L);

        assertThat(setupService.needsAdminSetup()).isTrue();
    }

    @Test
    void needsAdminSetup_whenUsersExist_returnsFalse() {
        when(userRepository.count()).thenReturn(1L);

        assertThat(setupService.needsAdminSetup()).isFalse();
    }

    @Test
    void needsAdminSetup_whenRepositoryThrows_returnsTrue() {
        when(userRepository.count()).thenThrow(new RuntimeException("DB down"));

        assertThat(setupService.needsAdminSetup()).isTrue();
    }
}
