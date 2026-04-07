package com.example.orderapi.config;

import com.example.orderapi.domain.Address;
import com.example.orderapi.domain.AddressType;
import com.example.orderapi.domain.Customer;
import com.example.orderapi.domain.Product;
import com.example.orderapi.domain.ProductDimensions;
import com.example.orderapi.domain.ProductPhysical;
import com.example.orderapi.domain.ProductPrice;
import com.example.orderapi.domain.ProductShipping;
import com.example.orderapi.domain.ProductStatus;
import com.example.orderapi.domain.ProductWeight;
import com.example.orderapi.repository.CustomerRepository;
import com.example.orderapi.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@Profile("!test")
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public DataInitializer(CustomerRepository customerRepository,
                           ProductRepository productRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedCustomers();
        seedProducts();
    }

    private void seedCustomers() {
        if (customerRepository.count() > 0) {
            log.debug("Customers already seeded, skipping");
            return;
        }

        List<Customer> customers = CUSTOMER_SEEDS.stream()
                .map(this::buildCustomer)
                .toList();

        customerRepository.saveAll(customers);
        log.info("Seeded {} customers", customers.size());
    }

    private void seedProducts() {
        if (productRepository.count() > 0) {
            log.debug("Products already seeded, skipping");
            return;
        }

        List<Product> products = PRODUCT_SEEDS.stream()
                .map(this::buildProduct)
                .toList();

        productRepository.saveAll(products);
        log.info("Seeded {} products", products.size());
    }

    private Customer buildCustomer(CustomerSeed seed) {
        Customer customer = new Customer();
        customer.setFirstName(seed.firstName());
        customer.setLastName(seed.lastName());
        customer.setEmail(seed.email());
        customer.setPhone(seed.phone());

        Address address = new Address();
        address.setType(AddressType.SHIPPING);
        address.setLine1(seed.line1());
        address.setCity(seed.city());
        address.setState(seed.state());
        address.setPostalCode(seed.postalCode());
        address.setCountry(seed.country());
        address.setDefaultAddress(true);

        customer.addAddress(address);
        return customer;
    }

    private Product buildProduct(ProductSeed seed) {
        Product product = new Product();
        product.setSku(seed.sku());
        product.setName(seed.name());
        product.setDescription(seed.description());
        product.setCategory(seed.category());
        product.setPrice(buildPrice(seed.amount(), seed.currency()));
        product.setPhysical(buildPhysical(seed));
        product.setShipping(buildShipping(seed));
        product.setStatus(buildStatus(seed));
        product.setTags(new LinkedHashSet<>(seed.tags()));
        return product;
    }

    private ProductPhysical buildPhysical(ProductSeed seed) {
        ProductPhysical physical = new ProductPhysical();
        ProductWeight weight = new ProductWeight();
        weight.setValue(seed.weightValue());
        weight.setUnit(seed.weightUnit());
        ProductDimensions dimensions = new ProductDimensions();
        dimensions.setLength(seed.length());
        dimensions.setWidth(seed.width());
        dimensions.setHeight(seed.height());
        dimensions.setUnit(seed.dimensionsUnit());
        physical.setWeight(weight);
        physical.setDimensions(dimensions);
        return physical;
    }

    private ProductPrice buildPrice(BigDecimal amount, String currency) {
        ProductPrice price = new ProductPrice();
        price.setAmount(amount);
        price.setCurrency(currency);
        return price;
    }

    private ProductShipping buildShipping(ProductSeed seed) {
        ProductShipping shipping = new ProductShipping();
        shipping.setFragile(seed.fragile());
        shipping.setHazmat(seed.hazmat());
        shipping.setRequiresCooling(seed.requiresCooling());
        shipping.setMaxStackable(seed.maxStackable());
        return shipping;
    }

    private ProductStatus buildStatus(ProductSeed seed) {
        ProductStatus status = new ProductStatus();
        status.setActive(seed.active());
        status.setShippable(seed.shippable());
        return status;
    }

    private static final List<CustomerSeed> CUSTOMER_SEEDS = List.of(
            new CustomerSeed("Ava", "Thompson", "ava.thompson@example.com", "+1-416-555-0101",
                    "17 Maple Ave", "Toronto", "ON", "M1A 1A1", "CA"),
            new CustomerSeed("Liam", "Patel", "liam.patel@example.com", "+1-416-555-0102",
                    "248 Queen St", "Toronto", "ON", "M5V 2B7", "CA"),
            new CustomerSeed("Zoe", "Martin", "zoe.martin@example.com", "+1-416-555-0103",
                    "92 Yonge St", "Toronto", "ON", "M5C 1R6", "CA"),
            new CustomerSeed("Ethan", "Brooks", "ethan.brooks@example.com", "+1-416-555-0104",
                    "310 King St", "Toronto", "ON", "M5V 1J8", "CA"),
            new CustomerSeed("Mia", "Li", "mia.li@example.com", "+1-416-555-0105",
                    "15 Bayview Rd", "Toronto", "ON", "M4G 3C4", "CA"),
            new CustomerSeed("Noah", "Rivera", "noah.rivera@example.com", "+1-416-555-0106",
                    "44 Dundas St", "Toronto", "ON", "M5G 2C5", "CA"),
            new CustomerSeed("Emma", "Foster", "emma.foster@example.com", "+1-416-555-0107",
                    "501 College St", "Toronto", "ON", "M6G 1A4", "CA"),
            new CustomerSeed("Lucas", "Hart", "lucas.hart@example.com", "+1-416-555-0108",
                    "800 Bathurst St", "Toronto", "ON", "M5P 3M5", "CA"),
            new CustomerSeed("Aria", "Vega", "aria.vega@example.com", "+1-416-555-0109",
                    "120 Spadina Ave", "Toronto", "ON", "M5T 2B3", "CA"),
            new CustomerSeed("Oliver", "Young", "oliver.young@example.com", "+1-416-555-0110",
                    "200 Front St", "Toronto", "ON", "M5V 0L5", "CA")
    );

    private static final List<ProductSeed> PRODUCT_SEEDS = List.of(
            new ProductSeed("WM-1001", "Wireless Mouse", "Ergonomic Bluetooth mouse", "Electronics",
                    new BigDecimal("29.99"), "USD", new BigDecimal("0.20"), "kg",
                    new BigDecimal("10"), new BigDecimal("6"), new BigDecimal("4"), "cm",
                    false, false, false, 10, true, true, List.of("mouse", "wireless")),
            new ProductSeed("KB-2001", "Mechanical Keyboard", "Tactile RGB keyboard", "Electronics",
                    new BigDecimal("89.99"), "USD", new BigDecimal("1.20"), "kg",
                    new BigDecimal("43"), new BigDecimal("14"), new BigDecimal("4"), "cm",
                    false, false, false, 5, true, true, List.of("keyboard", "mechanical")),
            new ProductSeed("HP-3001", "Noise-cancel Headphones", "Over-ear ANC headphones", "Audio",
                    new BigDecimal("149.99"), "USD", new BigDecimal("0.45"), "kg",
                    new BigDecimal("19"), new BigDecimal("18"), new BigDecimal("8"), "cm",
                    false, false, false, 6, true, true, List.of("audio", "headphones")),
            new ProductSeed("HB-4001", "USB-C Hub", "7-port USB-C hub", "Accessories",
                    new BigDecimal("59.99"), "USD", new BigDecimal("0.12"), "kg",
                    new BigDecimal("12"), new BigDecimal("6"), new BigDecimal("2"), "cm",
                    false, false, false, 20, true, true, List.of("hub", "usb-c")),
            new ProductSeed("SS-5001", "Portable SSD", "1TB NVMe SSD", "Storage",
                    new BigDecimal("119.99"), "USD", new BigDecimal("0.05"), "kg",
                    new BigDecimal("10"), new BigDecimal("6"), new BigDecimal("1"), "cm",
                    false, false, false, 25, true, true, List.of("storage", "ssd")),
            new ProductSeed("CH-6001", "Ergonomic Chair", "Adjustable mesh chair", "Furniture",
                    new BigDecimal("249.99"), "USD", new BigDecimal("17"), "kg",
                    new BigDecimal("70"), new BigDecimal("70"), new BigDecimal("120"), "cm",
                    true, false, false, 2, true, true, List.of("furniture", "chair")),
            new ProductSeed("MT-7001", "Standing Desk Mat", "Anti-fatigue mat", "Furniture",
                    new BigDecimal("59.99"), "USD", new BigDecimal("2.5"), "kg",
                    new BigDecimal("120"), new BigDecimal("60"), new BigDecimal("2"), "cm",
                    false, false, false, 15, true, true, List.of("furniture", "mat")),
            new ProductSeed("LP-8001", "Smart Lamp", "RGB desk lamp", "Home",
                    new BigDecimal("79.99"), "USD", new BigDecimal("1.8"), "kg",
                    new BigDecimal("20"), new BigDecimal("14"), new BigDecimal("44"), "cm",
                    false, false, false, 12, true, true, List.of("lighting", "smart")),
            new ProductSeed("WC-9001", "Web Cam", "1080p streaming webcam", "Electronics",
                    new BigDecimal("99.99"), "USD", new BigDecimal("0.25"), "kg",
                    new BigDecimal("7"), new BigDecimal("6"), new BigDecimal("6"), "cm",
                    false, false, false, 10, true, true, List.of("camera", "streaming")),
            new ProductSeed("SP-1001", "Bluetooth Speaker", "Portable stereo speaker", "Audio",
                    new BigDecimal("129.99"), "USD", new BigDecimal("1.1"), "kg",
                    new BigDecimal("22"), new BigDecimal("10"), new BigDecimal("9"), "cm",
                    false, false, false, 8, true, true, List.of("audio", "speaker"))
    );

    private record CustomerSeed(String firstName,
                                String lastName,
                                String email,
                                String phone,
                                String line1,
                                String city,
                                String state,
                                String postalCode,
                                String country) {
    }

    private record ProductSeed(String sku,
                               String name,
                               String description,
                               String category,
                               BigDecimal amount,
                               String currency,
                               BigDecimal weightValue,
                               String weightUnit,
                               BigDecimal length,
                               BigDecimal width,
                               BigDecimal height,
                               String dimensionsUnit,
                               boolean fragile,
                               boolean hazmat,
                               boolean requiresCooling,
                               int maxStackable,
                               boolean active,
                               boolean shippable,
                               List<String> tags) {
    }
}
