package com.flashsale.productdomain.domain.repository;

import com.flashsale.productdomain.domain.model.ReviewMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewMediaRepository extends JpaRepository<ReviewMedia, UUID> {
}
