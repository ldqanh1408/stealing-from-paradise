package com.flashsale.searchservice.service;

import com.flashsale.searchservice.dto.SearchResponse;
import com.flashsale.searchservice.dto.SuggestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchQueryServiceTest {

    @Mock
    private ElasticsearchService esService;

    @InjectMocks
    private SearchQueryService searchQueryService;

    @Test
    void searchShouldCapSizeAtMax40() {
        SearchResponse mockResponse = SearchResponse.builder().build();
        when(esService.search(anyString(), any(), any(), any(), anyBoolean(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(mockResponse);

        searchQueryService.search("test", null, null, null, null, null, "relevance", 0, 100);

        verify(esService).search(eq("test"), isNull(), isNull(), isNull(),
                eq(true), isNull(), eq("relevance"), eq(0), eq(40));
    }

    @Test
    void searchShouldDefaultInStockToTrue() {
        SearchResponse mockResponse = SearchResponse.builder().build();
        when(esService.search(anyString(), any(), any(), any(), anyBoolean(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(mockResponse);

        searchQueryService.search("test", null, null, null, null, null, "relevance", 0, 20);

        verify(esService).search(eq("test"), isNull(), isNull(), isNull(),
                eq(true), isNull(), eq("relevance"), eq(0), eq(20));
    }

    @Test
    void searchShouldPassInStockFalseWhenExplicitlySet() {
        SearchResponse mockResponse = SearchResponse.builder().build();
        when(esService.search(anyString(), any(), any(), any(), anyBoolean(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(mockResponse);

        searchQueryService.search("test", null, null, null, false, null, "relevance", 0, 20);

        verify(esService).search(eq("test"), isNull(), isNull(), isNull(),
                eq(false), isNull(), eq("relevance"), eq(0), eq(20));
    }

    @Test
    void searchShouldClampNegativePageToZero() {
        SearchResponse mockResponse = SearchResponse.builder().build();
        when(esService.search(anyString(), any(), any(), any(), anyBoolean(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(mockResponse);

        searchQueryService.search("test", null, null, null, null, null, "relevance", -5, 20);

        verify(esService).search(anyString(), isNull(), isNull(), isNull(),
                anyBoolean(), isNull(), anyString(), eq(0), anyInt());
    }

    @Test
    void searchShouldClampSizeBelowOneTo20() {
        SearchResponse mockResponse = SearchResponse.builder().build();
        when(esService.search(anyString(), any(), any(), any(), anyBoolean(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(mockResponse);

        searchQueryService.search("test", null, null, null, null, null, "relevance", 0, 0);

        verify(esService).search(anyString(), isNull(), isNull(), isNull(),
                anyBoolean(), isNull(), anyString(), anyInt(), eq(20));
    }

    @Test
    void suggestShouldReturnEmptyWhenQueryTooShort() {
        SuggestResponse result = searchQueryService.suggest("a", 5);

        assertNotNull(result.getSuggestions());
        assertTrue(result.getSuggestions().isEmpty());
        verify(esService, never()).suggest(anyString(), anyInt());
    }

    @Test
    void suggestShouldReturnEmptyWhenQueryNull() {
        SuggestResponse result = searchQueryService.suggest(null, 5);

        assertNotNull(result.getSuggestions());
        assertTrue(result.getSuggestions().isEmpty());
    }

    @Test
    void suggestShouldCapSizeAt10() {
        SuggestResponse mockResponse = SuggestResponse.builder()
                .suggestions(java.util.List.of("suggestion1"))
                .build();
        when(esService.suggest(anyString(), anyInt())).thenReturn(mockResponse);

        searchQueryService.suggest("test", 50);

        verify(esService).suggest("test", 10);
    }

    @Test
    void suggestShouldClampSizeBelowOneTo5() {
        SuggestResponse mockResponse = SuggestResponse.builder()
                .suggestions(java.util.List.of("suggestion1"))
                .build();
        when(esService.suggest(anyString(), anyInt())).thenReturn(mockResponse);

        searchQueryService.suggest("test", 0);

        verify(esService).suggest("test", 5);
    }

    @Test
    void suggestShouldDelegateToEsService() {
        SuggestResponse mockResponse = SuggestResponse.builder()
                .suggestions(java.util.List.of("apple", "application"))
                .build();
        when(esService.suggest("app", 5)).thenReturn(mockResponse);

        SuggestResponse result = searchQueryService.suggest("app", 5);

        assertEquals(2, result.getSuggestions().size());
        verify(esService).suggest("app", 5);
    }
}
