package com.example.orderapi.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "customer_addresses")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String addressId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AddressType type;

    @Column(nullable = false)
    private String line1;

    private String line2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String postalCode;

    @Column(nullable = false, length = 2)
    private String country;

    @Column(nullable = false)
    private boolean defaultAddress;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @PrePersist
    void assignAddressId() {
        if (addressId == null || addressId.isBlank()) {
            addressId = UUID.randomUUID().toString();
        }
    }

    public Long getId() {
        return id;
    }

    public String getAddressId() {
        return addressId;
    }

    public AddressType getType() {
        return type;
    }

    public String getLine1() {
        return line1;
    }

    public String getLine2() {
        return line2;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCountry() {
        return country;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setAddressId(String addressId) {
        this.addressId = addressId;
    }

    public void setType(AddressType type) {
        this.type = type;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public void setDefaultAddress(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
}
