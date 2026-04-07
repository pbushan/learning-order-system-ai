package com.example.orderapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
public class ProductRequest {

    @NotBlank(message = "sku is required")
    private String sku;

    @NotBlank(message = "name is required")
    private String name;

    @NotBlank(message = "description is required")
    @Size(max = 1024, message = "description must be 1024 characters or fewer")
    private String description;

    @NotNull(message = "price is required")
    @Valid
    private PriceRequest price;

    @NotNull(message = "physical is required")
    @Valid
    private PhysicalRequest physical;

    @NotNull(message = "shipping is required")
    @Valid
    private ShippingRequest shipping;

    @NotNull(message = "status is required")
    @Valid
    private StatusRequest status;

    @NotBlank(message = "category is required")
    private String category;

    @NotEmpty(message = "tags are required")
    private List<@NotBlank(message = "tags cannot contain empty values") String> tags;

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public PriceRequest getPrice() {
        return price;
    }

    public PhysicalRequest getPhysical() {
        return physical;
    }

    public ShippingRequest getShipping() {
        return shipping;
    }

    public StatusRequest getStatus() {
        return status;
    }

    public String getCategory() {
        return category;
    }

    public List<String> getTags() {
        return tags;
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

    public void setPrice(PriceRequest price) {
        this.price = price;
    }

    public void setPhysical(PhysicalRequest physical) {
        this.physical = physical;
    }

    public void setShipping(ShippingRequest shipping) {
        this.shipping = shipping;
    }

    public void setStatus(StatusRequest status) {
        this.status = status;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public static class PriceRequest {
        @NotNull(message = "price.amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "price.amount must be positive")
        private BigDecimal amount;

        @NotBlank(message = "price.currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "price.currency must be a 3-letter ISO code")
        private String currency;

        public BigDecimal getAmount() {
            return amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }

    public static class PhysicalRequest {
        @NotNull(message = "physical.weight is required")
        @Valid
        private WeightRequest weight;

        @NotNull(message = "physical.dimensions is required")
        @Valid
        private DimensionsRequest dimensions;

        public WeightRequest getWeight() {
            return weight;
        }

        public DimensionsRequest getDimensions() {
            return dimensions;
        }

        public void setWeight(WeightRequest weight) {
            this.weight = weight;
        }

        public void setDimensions(DimensionsRequest dimensions) {
            this.dimensions = dimensions;
        }
    }

    public static class WeightRequest {
        @NotNull(message = "physical.weight.value is required")
        @DecimalMin(value = "0", inclusive = false, message = "physical.weight.value must be positive")
        private BigDecimal value;

        @NotBlank(message = "physical.weight.unit is required")
        private String unit;

        public BigDecimal getValue() {
            return value;
        }

        public String getUnit() {
            return unit;
        }

        public void setValue(BigDecimal value) {
            this.value = value;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }
    }

    public static class DimensionsRequest {
        @NotNull(message = "physical.dimensions.length is required")
        @DecimalMin(value = "0", inclusive = false, message = "physical.dimensions.length must be positive")
        private BigDecimal length;

        @NotNull(message = "physical.dimensions.width is required")
        @DecimalMin(value = "0", inclusive = false, message = "physical.dimensions.width must be positive")
        private BigDecimal width;

        @NotNull(message = "physical.dimensions.height is required")
        @DecimalMin(value = "0", inclusive = false, message = "physical.dimensions.height must be positive")
        private BigDecimal height;

        @NotBlank(message = "physical.dimensions.unit is required")
        private String unit;

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

        public void setLength(BigDecimal length) {
            this.length = length;
        }

        public void setWidth(BigDecimal width) {
            this.width = width;
        }

        public void setHeight(BigDecimal height) {
            this.height = height;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }
    }

    public static class ShippingRequest {
        @NotNull(message = "shipping.fragile is required")
        private Boolean fragile;

        @NotNull(message = "shipping.hazmat is required")
        private Boolean hazmat;

        @NotNull(message = "shipping.requiresCooling is required")
        private Boolean requiresCooling;

        @NotNull(message = "shipping.maxStackable is required")
        @Min(value = 1, message = "shipping.maxStackable must be at least 1")
        private Integer maxStackable;

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

        public void setFragile(Boolean fragile) {
            this.fragile = fragile;
        }

        public void setHazmat(Boolean hazmat) {
            this.hazmat = hazmat;
        }

        public void setRequiresCooling(Boolean requiresCooling) {
            this.requiresCooling = requiresCooling;
        }

        public void setMaxStackable(Integer maxStackable) {
            this.maxStackable = maxStackable;
        }
    }

    public static class StatusRequest {
        @NotNull(message = "status.active is required")
        private Boolean active;

        @NotNull(message = "status.shippable is required")
        private Boolean shippable;

        public Boolean getActive() {
            return active;
        }

        public Boolean getShippable() {
            return shippable;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public void setShippable(Boolean shippable) {
            this.shippable = shippable;
        }
    }
}
