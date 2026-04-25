package com.flashsale.identitydomain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppealCreateRequest {
    private Long logId;
    private String reason;
    private List<String> evidenceUrls;
}
