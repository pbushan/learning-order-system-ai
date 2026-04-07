package com.example.orderapi.service;

import com.example.orderapi.domain.*;
import com.example.orderapi.dto.ProductRequest;
import com.example.orderapi.exception.ResourceNotFoundException;
import com.example.orderapi.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(ProductRequest request) {
        ensureSkuUnique(request.getSku());
        Product product = buildFromRequest(request);
        return productRepository.save(product);
    }

    public List<Product> getAll() {
        return productRepository.findAll();
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public Product update(Long id, ProductRequest request) {
        Product existing = getById(id);
        if (!existing.getSku().equalsIgnoreCase(request.getSku())) {
            ensureSkuUnique(request.getSku());
        }
        applyRequest(existing, request);
        return productRepository.save(existing);
    }

    public void delete(Long id) {
        Product existing = getById(id);
        productRepository.delete(existing);
    }

    private void ensureSkuUnique(String sku) {
        String normalized = sku.trim();
        if (productRepository.existsBySku(normalized)) {
            throw new IllegalArgumentException("SKU already exists: " + normalized);
        }
    }

    private Product buildFromRequest(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return product;
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setSku(request.getSku().trim());
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription().trim());
        product.setPrice(buildPrice(request.getPrice()));
        product.setPhysical(buildPhysical(request.getPhysical()));
        product.setShipping(buildShipping(request.getShipping()));
        product.setStatus(buildStatus(request.getStatus()));
        product.setCategory(request.getCategory().trim());
        product.setTags(buildTags(request.getTags()));
    }

    private ProductPrice buildPrice(ProductRequest.PriceRequest priceRequest) {
        ProductPrice price = new ProductPrice();
        price.setAmount(priceRequest.getAmount());
        price.setCurrency(priceRequest.getCurrency().trim().toUpperCase());
        return price;
    }

    private ProductPhysical buildPhysical(ProductRequest.PhysicalRequest physicalRequest) {
        ProductPhysical physical = new ProductPhysical();
        ProductWeight weight = new ProductWeight();
        weight.setValue(physicalRequest.getWeight().getValue());
        weight.setUnit(physicalRequest.getWeight().getUnit().trim());

        ProductDimensions dimensions = new ProductDimensions();
        dimensions.setLength(physicalRequest.getDimensions().getLength());
        dimensions.setWidth(physicalRequest.getDimensions().getWidth());
        dimensions.setHeight(physicalRequest.getDimensions().getHeight());
        dimensions.setUnit(physicalRequest.getDimensions().getUnit().trim());

        physical.setWeight(weight);
        physical.setDimensions(dimensions);
        return physical;
    }

    private ProductShipping buildShipping(ProductRequest.ShippingRequest shippingRequest) {
        ProductShipping shipping = new ProductShipping();
        shipping.setFragile(shippingRequest.getFragile());
        shipping.setHazmat(shippingRequest.getHazmat());
        shipping.setRequiresCooling(shippingRequest.getRequiresCooling());
        shipping.setMaxStackable(shippingRequest.getMaxStackable());
        return shipping;
    }

    private ProductStatus buildStatus(ProductRequest.StatusRequest statusRequest) {
        ProductStatus status = new ProductStatus();
        status.setActive(statusRequest.getActive());
        status.setShippable(statusRequest.getShippable());
        return status;
    }

    private LinkedHashSet<String> buildTags(List<String> tags) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : tags) {
            normalized.add(tag.trim());
        }
        return normalized;
    }
}
