package com.flashsale.identitydomain.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressCreateRequest {
    private Integer provinceId;
    private Integer districtId;
    private String fullAddress;
    private Boolean isDefault;
}
