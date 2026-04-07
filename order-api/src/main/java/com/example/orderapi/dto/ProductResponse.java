package com.example.orderapi.dto;

import com.example.orderapi.domain.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ProductResponse {

    private Long id;
    private String productId;
    private String sku;
    private String name;
    private String description;
    private PriceResponse price;
    private PhysicalResponse physical;
    private ShippingResponse shipping;
    private StatusResponse status;
    private String category;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.productId = product.getProductId();
        response.sku = product.getSku();
        response.name = product.getName();
        response.description = product.getDescription();
        response.price = new PriceResponse(product.getPrice());
        response.physical = new PhysicalResponse(product.getPhysical());
        response.shipping = new ShippingResponse(product.getShipping());
        response.status = new StatusResponse(product.getStatus());
        response.category = product.getCategory();
        response.tags = product.getTags().stream().toList();
        response.createdAt = product.getCreatedAt();
        response.updatedAt = product.getUpdatedAt();
        return response;
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

    public PriceResponse getPrice() {
        return price;
    }

    public PhysicalResponse getPhysical() {
        return physical;
    }

    public ShippingResponse getShipping() {
        return shipping;
    }

    public StatusResponse getStatus() {
        return status;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getTags() {
        return tags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public static class PriceResponse {
        private BigDecimal amount;
        private String currency;

        public PriceResponse(ProductPrice price) {
            if (price != null) {
                this.amount = price.getAmount();
                this.currency = price.getCurrency();
            }
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getCurrency() {
            return currency;
        }
    }

    public static class PhysicalResponse {
        private WeightResponse weight;
        private DimensionsResponse dimensions;

        public PhysicalResponse(ProductPhysical physical) {
            if (physical != null) {
                this.weight = new WeightResponse(physical.getWeight());
                this.dimensions = new DimensionsResponse(physical.getDimensions());
            }
        }

        public WeightResponse getWeight() {
            return weight;
        }

        public DimensionsResponse getDimensions() {
            return dimensions;
        }
    }

    public static class WeightResponse {
        private BigDecimal value;
        private String unit;

        public WeightResponse(ProductWeight weight) {
            if (weight != null) {
                this.value = weight.getValue();
                this.unit = weight.getUnit();
            }
        }

        public BigDecimal getValue() {
            return value;
        }

        public String getUnit() {
            return unit;
        }
    }

    public static class DimensionsResponse {
        private BigDecimal length;
        private BigDecimal width;
        private BigDecimal height;
        private String unit;

        public DimensionsResponse(ProductDimensions dimensions) {
            if (dimensions != null) {
                this.length = dimensions.getLength();
                this.width = dimensions.getWidth();
                this.height = dimensions.getHeight();
                this.unit = dimensions.getUnit();
            }
        }

        public BigDecimal getLength() {
            return length;
        }

        public BigDecimal getWidth() {
            return width;
        }

        public BigDecimal getHeight() {
            return height;
        }

        public String getUnit() {
            return unit;
        }
    }

    public static class ShippingResponse {
        private Boolean fragile;
        private Boolean hazmat;
        private Boolean requiresCooling;
        private Integer maxStackable;

        public ShippingResponse(ProductShipping shipping) {
            if (shipping != null) {
                this.fragile = shipping.getFragile();
                this.hazmat = shipping.getHazmat();
                this.requiresCooling = shipping.getRequiresCooling();
                this.maxStackable = shipping.getMaxStackable();
            }
        }

        public Boolean getFragile() {
            return fragile;
        }

        public Boolean getHazmat() {
            return hazmat;
        }

        public Boolean getRequiresCooling() {
            return requiresCooling;
        }

        public Integer getMaxStackable() {
            return maxStackable;
        }
    }

    public static class StatusResponse {
        private Boolean active;
        private Boolean shippable;

        public StatusResponse(ProductStatus status) {
            if (status != null) {
                this.active = status.getActive();
                this.shippable = status.getShippable();
            }
        }

        public Boolean getActive() {
            return active;
        }

        public Boolean getShippable() {
            return shippable;
        }
    }
}
