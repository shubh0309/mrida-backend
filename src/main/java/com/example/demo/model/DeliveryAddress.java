package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipientName;

    @Column
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "address_line", nullable = false, length = 1024)
    private String addressLine;

    @Column(length = 512)
    private String addressLine1;

    @Column(length = 512)
    private String addressLine2;

    @Column(nullable = false, length = 6)
    private String pinCode;

    private String landmark;

    @Column(nullable = false)
    private String city;

    /** Short code aligned with frontend dropdown (see IndiaLocationReferenceService). */
    @Column(nullable = false)
    private String stateCode;

    @Column(nullable = false)
    private String stateName;

}
