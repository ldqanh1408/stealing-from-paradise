package com.flashsale.flashsaleservice.service;

import com.flashsale.flashsaleservice.domain.model.FlashSaleItem;
import com.flashsale.flashsaleservice.domain.model.FlashSaleReminder;
import com.flashsale.flashsaleservice.domain.model.FlashSaleSession;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleItemRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleReminderRepository;
import com.flashsale.flashsaleservice.domain.repository.FlashSaleSessionRepository;
import com.flashsale.flashsaleservice.dto.request.CreateFlashSaleItemRequest;
import com.flashsale.flashsaleservice.dto.request.CreateSessionRequest;
import com.flashsale.flashsaleservice.dto.response.FlashSaleItemResponse;
import com.flashsale.flashsaleservice.dto.response.SessionDetailResponse;
import com.flashsale.flashsaleservice.dto.response.SessionListResponse;
import com.flashsale.flashsaleservice.dto.response.SessionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashSaleServiceTest {

    @Mock
    private FlashSaleSessionRepository sessionRepo;

    @Mock
    private FlashSaleItemRepository itemRepo;

    @Mock
    private FlashSaleReminderRepository reminderRepo;

    @InjectMocks
    private FlashSaleService flashSaleService;

    @Test
    void getSessionsShouldReturnAllWhenNoStatusFilter() {
        FlashSaleSession session = FlashSaleSession.builder()
                .id(1L)
                .name("Test Session")
                .status("ACTIVE")
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .build();
        when(sessionRepo.findAll()).thenReturn(Flux.just(session));

        Mono<SessionListResponse> result = flashSaleService.getSessions(null);

        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getSessions().size() == 1 &&
                        response.getSessions().get(0).getName().equals("Test Session") &&
                        response.getServerTime() > 0)
                .verifyComplete();
    }

    @Test
    void getSessionsShouldFilterByStatus() {
        when(sessionRepo.findByStatus("UPCOMING")).thenReturn(Flux.empty());

        Mono<SessionListResponse> result = flashSaleService.getSessions("UPCOMING");

        StepVerifier.create(result)
                .expectNextMatches(response -> response.getSessions().isEmpty())
                .verifyComplete();
    }

    @Test
    void getSessionDetailShouldReturnSessionWithItems() {
        FlashSaleSession session = FlashSaleSession.builder()
                .id(1L)
                .name("Flash Sale")
                .status("ACTIVE")
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .build();
        FlashSaleItem item = FlashSaleItem.builder()
                .id(10L)
                .sessionId(1L)
                .skuCode("SKU-001")
                .flashPrice(new BigDecimal("99.99"))
                .flashStock(100)
                .limitPerUser(2)
                .soldQty(0)
                .status("APPROVED")
                .build();
        when(sessionRepo.findById(1L)).thenReturn(Mono.just(session));
        when(itemRepo.findBySessionId(1L)).thenReturn(Flux.just(item));

        Mono<SessionDetailResponse> result = flashSaleService.getSessionDetail(1L);

        StepVerifier.create(result)
                .expectNextMatches(detail ->
                        detail.getSession().getName().equals("Flash Sale") &&
                        detail.getItems().size() == 1 &&
                        detail.getItems().get(0).getSkuCode().equals("SKU-001"))
                .verifyComplete();
    }

    @Test
    void getSessionDetailShouldReturnEmptyWhenNotFound() {
        when(sessionRepo.findById(99L)).thenReturn(Mono.empty());

        Mono<SessionDetailResponse> result = flashSaleService.getSessionDetail(99L);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void createFlashSaleItemShouldSaveAndReturnItem() {
        CreateFlashSaleItemRequest req = CreateFlashSaleItemRequest.builder()
                .skuCode("SKU-002")
                .flashPrice(new BigDecimal("49.99"))
                .flashStock(50)
                .limitPerUser(1)
                .build();
        FlashSaleItem saved = FlashSaleItem.builder()
                .id(20L)
                .sessionId(1L)
                .skuCode("SKU-002")
                .flashPrice(new BigDecimal("49.99"))
                .flashStock(50)
                .limitPerUser(1)
                .soldQty(0)
                .status("PENDING")
                .build();
        when(itemRepo.save(any(FlashSaleItem.class))).thenReturn(Mono.just(saved));

        Mono<FlashSaleItemResponse> result = flashSaleService.createFlashSaleItem(1L, req);

        StepVerifier.create(result)
                .expectNextMatches(resp ->
                        resp.getSkuCode().equals("SKU-002") &&
                        resp.getStatus().equals("PENDING") &&
                        resp.getFlashStock() == 50)
                .verifyComplete();
    }

    @Test
    void createSessionShouldSaveAndReturnSession() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(2);
        CreateSessionRequest req = CreateSessionRequest.builder()
                .name("New Sale")
                .startTime(start)
                .endTime(end)
                .build();
        FlashSaleSession saved = FlashSaleSession.builder()
                .id(1L)
                .name("New Sale")
                .startTime(start)
                .endTime(end)
                .status("UPCOMING")
                .build();
        when(sessionRepo.save(any(FlashSaleSession.class))).thenReturn(Mono.just(saved));

        Mono<SessionResponse> result = flashSaleService.createSession(req);

        StepVerifier.create(result)
                .expectNextMatches(resp ->
                        resp.getName().equals("New Sale") &&
                        resp.getStatus().equals("UPCOMING"))
                .verifyComplete();
    }

    @Test
    void setReminderShouldCreateWhenNotExists() {
        when(reminderRepo.findByCustomerIdAndSessionId(1L, 1L))
                .thenReturn(Mono.empty());
        when(reminderRepo.save(any(FlashSaleReminder.class)))
                .thenReturn(Mono.just(FlashSaleReminder.builder().customerId(1L).sessionId(1L).build()));

        Mono<Void> result = flashSaleService.setReminder(1L, 1L);

        StepVerifier.create(result).verifyComplete();
        verify(reminderRepo).save(any(FlashSaleReminder.class));
    }

    @Test
    void setReminderShouldNotDuplicateWhenExists() {
        FlashSaleReminder existing = FlashSaleReminder.builder()
                .customerId(1L).sessionId(1L).build();
        when(reminderRepo.findByCustomerIdAndSessionId(1L, 1L))
                .thenReturn(Mono.just(existing));

        Mono<Void> result = flashSaleService.setReminder(1L, 1L);

        StepVerifier.create(result).verifyComplete();
        verify(reminderRepo, never()).save(any());
    }

    @Test
    void removeReminderShouldDeleteWhenExists() {
        FlashSaleReminder existing = FlashSaleReminder.builder()
                .customerId(1L).sessionId(1L).build();
        when(reminderRepo.findByCustomerIdAndSessionId(1L, 1L))
                .thenReturn(Mono.just(existing));
        when(reminderRepo.delete(existing)).thenReturn(Mono.empty());

        Mono<Void> result = flashSaleService.removeReminder(1L, 1L);

        StepVerifier.create(result).verifyComplete();
        verify(reminderRepo).delete(existing);
    }

    @Test
    void sessionResponseShouldMarkEndedWhenPast() {
        FlashSaleSession pastSession = FlashSaleSession.builder()
                .id(1L)
                .name("Past Sale")
                .status("ACTIVE")
                .startTime(LocalDateTime.now().minusHours(3))
                .endTime(LocalDateTime.now().minusHours(1))
                .build();
        when(sessionRepo.findAll()).thenReturn(Flux.just(pastSession));

        Mono<SessionListResponse> result = flashSaleService.getSessions(null);

        StepVerifier.create(result)
                .expectNextMatches(response ->
                        response.getSessions().get(0).isEnded())
                .verifyComplete();
    }
}
