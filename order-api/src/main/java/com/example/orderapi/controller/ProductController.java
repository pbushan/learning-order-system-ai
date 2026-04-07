package com.example.orderapi.controller;

import com.example.orderapi.dto.ProductRequest;
import com.example.orderapi.dto.ProductResponse;
import com.example.orderapi.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.create(request));
    }

    @GetMapping
    public List<ProductResponse> getAll() {
        return productService.getAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return ProductResponse.from(productService.getById(id));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
