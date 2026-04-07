package com.example.orderapi.service;

import com.example.orderapi.domain.Address;
import com.example.orderapi.domain.Customer;
import com.example.orderapi.dto.CustomerRequest;
import com.example.orderapi.exception.ResourceNotFoundException;
import com.example.orderapi.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer create(CustomerRequest request) {
        Customer customer = new Customer();
        applyRequest(customer, request);
        return customerRepository.save(customer);
    }

    public List<Customer> getAll() {
        return customerRepository.findAll();
    }

    public Customer getById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
    }

    public Customer update(Long id, CustomerRequest request) {
        Customer customer = getById(id);
        applyRequest(customer, request);
        return customerRepository.save(customer);
    }

    public void delete(Long id) {
        Customer customer = getById(id);
        customerRepository.delete(customer);
    }

    private void applyRequest(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.getName().getFirstName().trim());
        customer.setLastName(request.getName().getLastName().trim());
        customer.setName(("%s %s".formatted(
                request.getName().getFirstName().trim(),
                request.getName().getLastName().trim()
        )).trim());
        customer.setEmail(request.getEmail().trim().toLowerCase());
        customer.setPhone(request.getPhone().trim());

        List<Address> addresses = request.getAddresses().stream()
                .map(addressRequest -> {
                    Address address = new Address();
                    address.setType(addressRequest.getType());
                    address.setLine1(addressRequest.getLine1().trim());
                    address.setLine2(addressRequest.getLine2() == null ? null : addressRequest.getLine2().trim());
                    address.setCity(addressRequest.getCity().trim());
                    address.setState(addressRequest.getState().trim());
                    address.setPostalCode(addressRequest.getPostalCode().trim());
                    address.setCountry(addressRequest.getCountry().trim().toUpperCase());
                    address.setDefaultAddress(Boolean.TRUE.equals(addressRequest.getIsDefault()));
                    return address;
                })
                .toList();

        if (addresses.stream().noneMatch(Address::isDefaultAddress) && !addresses.isEmpty()) {
            addresses.get(0).setDefaultAddress(true);
        }
        customer.setAddresses(addresses);
    }
}
