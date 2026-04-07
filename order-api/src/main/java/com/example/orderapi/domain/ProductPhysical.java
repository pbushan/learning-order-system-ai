package com.example.orderapi.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProductPhysical {

    @Embedded
    private ProductWeight weight;

    @Embedded
    private ProductDimensions dimensions;

    public ProductWeight getWeight() {
        return weight;
    }

    public ProductDimensions getDimensions() {
        return dimensions;
    }

    public void setWeight(ProductWeight weight) {
        this.weight = weight;
    }

    public void setDimensions(ProductDimensions dimensions) {
        this.dimensions = dimensions;
    }
}
