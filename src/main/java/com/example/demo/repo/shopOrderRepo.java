package com.example.demo.repo;

import com.example.demo.model.ShopOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface shopOrderRepo extends JpaRepository<ShopOrder, Long> {

    Optional<ShopOrder> findByRazorpayOrderId(String razorpayOrderId);

    @EntityGraph(attributePaths = "lineItems")
    @Query("select o from ShopOrder o where o.id = :id")
    Optional<ShopOrder> findWithLineItemsById(@Param("id") Long id);

    @Query("""
            select o from ShopOrder o
            join o.deliveryAddress a
            where lower(a.email) = lower(:email)
              and lower(a.lastName) = lower(:lastName)
            order by o.createdAt desc
            """)
    List<ShopOrder> findByTrackingIdentity(@Param("email") String email, @Param("lastName") String lastName);
}
