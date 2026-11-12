package com.shineunsigan.snsg.place.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.place.application.ImportPlacesFacade;

@ExtendWith(MockitoExtension.class)
class PlaceImportSchedulerTest {

    @Mock
    private ImportPlacesFacade importPlacesFacade;

    @InjectMocks
    private PlaceImportScheduler scheduler;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("적재 동안 감사 주체는 SYSTEM_SCHEDULER이고, 끝나면 SecurityContext를 비운다")
    void importsAsScheduler() {
        List<Object> principals = new ArrayList<>();
        doAnswer(invocation -> {
            principals.add(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return null;
        }).when(importPlacesFacade).importPlaces();

        scheduler.importPlaces();

        assertThat(principals).containsExactly(Auditor.SYSTEM_SCHEDULER);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("적재가 예외로 끝나도 SecurityContext를 비운다")
    void clearsContextOnFailure() {
        doThrow(new IllegalStateException("boom")).when(importPlacesFacade).importPlaces();

        assertThatThrownBy(() -> scheduler.importPlaces()).isInstanceOf(IllegalStateException.class);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
