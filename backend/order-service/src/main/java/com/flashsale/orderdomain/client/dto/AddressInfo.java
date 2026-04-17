package com.flashsale.orderdomain.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Thông tin địa chỉ nhận từ Identity Service.
 */
@Data
public class AddressInfo {

    @JsonProperty("address_id")
    private Long addressId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("full_address")
    private String fullAddress;

    @JsonProperty("province_id")
    private Integer provinceId;

    @JsonProperty("district_id")
    private Integer districtId;
}
