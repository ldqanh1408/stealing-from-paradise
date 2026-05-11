package com.flashsale.identityservice.service;

import com.flashsale.identityservice.domain.model.Address;
import com.flashsale.identityservice.domain.model.Role;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.AddressRepository;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import com.flashsale.identityservice.domain.repository.UserRepository;
import com.flashsale.identityservice.dto.request.ChangePasswordRequest;
import com.flashsale.identityservice.dto.request.AddressCreateRequest;
import com.flashsale.identityservice.dto.request.AddressUpdateRequest;
import com.flashsale.identityservice.dto.request.UserProfileUpdateRequest;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.identityservice.dto.response.AddressResponse;
import com.flashsale.identityservice.dto.response.AdminUserResponse;
import com.flashsale.identityservice.dto.response.UserProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = getUserById(userId);
        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(Role::getRoleName)
                .orElse("BUYER");

        return UserProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional
    public UserProfileResponse updateUserProfile(Long userId, UserProfileUpdateRequest request) {
        User user = getUserById(userId);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            userRepository.findByPhone(request.getPhone())
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(ignored -> { throw new RuntimeException("Phone number already in use"); });
            user.setPhone(request.getPhone());
        }

        userRepository.save(user);
        return getUserProfile(userId);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressCreateRequest request) {
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultForUser(userId);
        }

        Address address = Address.builder()
                .userId(userId)
                .provinceId(request.getProvinceId())
                .districtId(request.getDistrictId())
                .fullAddress(request.getFullAddress())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .build();

        if (!address.getIsDefault() && addressRepository.countByUserId(userId) == 0) {
            address.setIsDefault(true);
        }

        return toAddressResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressUpdateRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (request.getProvinceId() != null) {
            address.setProvinceId(request.getProvinceId());
        }
        if (request.getDistrictId() != null) {
            address.setDistrictId(request.getDistrictId());
        }
        if (request.getFullAddress() != null) {
            address.setFullAddress(request.getFullAddress());
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultForUserExcept(userId, addressId);
            address.setIsDefault(true);
        }

        return toAddressResponse(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        boolean wasDefault = address.getIsDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                    .stream()
                    .filter(a -> !a.getId().equals(addressId))
                    .findFirst()
                    .ifPresent(first -> {
                        first.setIsDefault(true);
                        addressRepository.save(first);
                    });
        }
    }

    @Transactional
    public void registerAsSeller(Long userId) {
        User user = getUserById(userId);

        boolean alreadySeller = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(r -> "SELLER".equals(r.getRoleName()))
                .orElse(false);

        if (alreadySeller) {
            throw new RuntimeException("User is already a seller");
        }

        Role role = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .orElse(Role.builder().userId(userId).build());
        role.setRoleName("SELLER");
        roleRepository.save(role);

        log.info("User {} registered as seller", userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String status, String search, Pageable pageable) {
        Specification<User> spec = null;

        if (status != null && !status.isBlank()) {
            spec = (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            Specification<User> searchSpec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.and(
                            cb.isNotNull(root.get("phone")),
                            cb.like(cb.lower(root.get("phone")), pattern)
                    )
            );
            spec = (spec == null) ? searchSpec : spec.and(searchSpec);
        }

        Page<User> userPage = (spec != null)
                ? userRepository.findAll(spec, pageable)
                : userRepository.findAll(pageable);

        List<AdminUserResponse> responses = userPage.getContent().stream()
                .map(user -> {
                    String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                            .map(Role::getRoleName)
                            .orElse("BUYER");

                    return AdminUserResponse.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .fullName(user.getFullName())
                            .status(user.getStatus())
                            .role(roleName)
                            .createdAt(user.getCreatedAt())
                            .build();
                })
                .toList();

        return PageResponse.<AdminUserResponse>builder()
                .content(responses)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getAdminUserDetail(Long userId) {
        User user = getUserById(userId);
        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(Role::getRoleName)
                .orElse("BUYER");

        return AdminUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .role(roleName)
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user {}", userId);
    }

    private AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
                .addressId(address.getId())
                .provinceId(address.getProvinceId())
                .districtId(address.getDistrictId())
                .fullAddress(address.getFullAddress())
                .isDefault(address.getIsDefault())
                .build();
    }
}
