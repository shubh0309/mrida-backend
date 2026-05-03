package com.example.demo.repo;

import com.example.demo.model.CheckoutUserInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface checkoutUserInfoRepo extends JpaRepository<CheckoutUserInfo, Long> {
}
