package com.flashsale.productservice.domain.util;

import com.flashsale.productservice.domain.model.Inventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryOperationsTest {

    @Mock private MongoTemplate mongoTemplate;
    @InjectMocks private InventoryOperations inventoryOps;

    @Test
    void lockStock_sufficientStock_returnsTrue() {
        com.mongodb.client.result.UpdateResult result = mock(com.mongodb.client.result.UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(result);

        boolean locked = inventoryOps.lockStock("SKU-001", 2);

        assertThat(locked).isTrue();
        
        // Verify query uses camelCase field names (not snake_case)
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), any(Update.class), eq(Inventory.class));
        
        String queryString = queryCaptor.getValue().toString();
        assertThat(queryString).doesNotContain("sku_code");
        assertThat(queryString).doesNotContain("stock_available");
        assertThat(queryString).contains("skuCode");
        assertThat(queryString).contains("stockAvailable");
    }

    @Test
    void lockStock_insufficientStock_returnsFalse() {
        com.mongodb.client.result.UpdateResult result = mock(com.mongodb.client.result.UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(result);

        boolean locked = inventoryOps.lockStock("SKU-001", 100);

        assertThat(locked).isFalse();
    }

    @Test
    void unlockStock_releasesStock() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(mock(com.mongodb.client.result.UpdateResult.class));

        boolean unlocked = inventoryOps.unlockStock("SKU-001", 2);

        assertThat(unlocked).isTrue();
        
        // Verify camelCase field names
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), any(Update.class), eq(Inventory.class));
        assertThat(queryCaptor.getValue().toString()).contains("skuCode");
    }

    @Test
    void consumeLockedStock_validRequest_returnsTrue() {
        com.mongodb.client.result.UpdateResult result = mock(com.mongodb.client.result.UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(1L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(result);

        boolean consumed = inventoryOps.consumeLockedStock("SKU-001", 2);

        assertThat(consumed).isTrue();
    }

    @Test
    void reserveFlashStock_insufficientStock_returnsFalse() {
        com.mongodb.client.result.UpdateResult result = mock(com.mongodb.client.result.UpdateResult.class);
        when(result.getModifiedCount()).thenReturn(0L);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(Inventory.class)))
                .thenReturn(result);

        boolean reserved = inventoryOps.reserveFlashStock("SKU-001", 100);

        assertThat(reserved).isFalse();
    }
}
