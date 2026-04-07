package com.example.orderapi.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", unique = true, updatable = false, length = 36)
    private String productId;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 1024)
    private String description;

    @Embedded
    private ProductPrice price;

    @Embedded
    private ProductPhysical physical;

    @Embedded
    private ProductShipping shipping;

    @Embedded
    private ProductStatus status;

    @Column(nullable = false)
    private String category;

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_ref"))
    @Column(name = "tag", nullable = false)
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (productId == null || productId.isBlank()) {
            productId = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProductPrice getPrice() {
        return price;
    }

    public ProductPhysical getPhysical() {
        return physical;
    }

    public ProductShipping getShipping() {
        return shipping;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public String getCategory() {
        return category;
    }

    public Set<String> getTags() {
        return tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPrice(ProductPrice price) {
        this.price = price;
    }

    public void setPhysical(ProductPhysical physical) {
        this.physical = physical;
    }

    public void setShipping(ProductShipping shipping) {
        this.shipping = shipping;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setTags(Set<String> tags) {
        this.tags.clear();
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
