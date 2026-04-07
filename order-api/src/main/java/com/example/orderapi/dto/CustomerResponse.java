package com.example.orderapi.dto;

import com.example.orderapi.domain.Address;
import com.example.orderapi.domain.AddressType;
import com.example.orderapi.domain.Customer;

import java.time.LocalDateTime;
import java.util.List;

public class CustomerResponse {
    private Long id;
    private String customerId;
    private NameResponse name;
    private String email;
    private String phone;
    private List<AddressResponse> addresses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CustomerResponse from(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setCustomerId(customer.getCustomerId());
        response.setName(NameResponse.from(customer));
        response.setEmail(customer.getEmail());
        response.setPhone(customer.getPhone());
        response.setAddresses(customer.getAddresses().stream()
                .map(AddressResponse::from)
                .toList());
        response.setCreatedAt(customer.getCreatedAt());
        response.setUpdatedAt(customer.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public NameResponse getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public List<AddressResponse> getAddresses() {
        return addresses;
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

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public void setName(NameResponse name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setAddresses(List<AddressResponse> addresses) {
        this.addresses = addresses;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class NameResponse {
        private String firstName;
        private String lastName;

        public static NameResponse from(Customer customer) {
            NameResponse nameResponse = new NameResponse();
            if (customer.getFirstName() != null || customer.getLastName() != null) {
                nameResponse.setFirstName(customer.getFirstName());
                nameResponse.setLastName(customer.getLastName());
                return nameResponse;
            }

            String fullName = customer.getName() == null ? "" : customer.getName().trim();
            int splitIndex = fullName.lastIndexOf(" ");
            if (splitIndex <= 0) {
                nameResponse.setFirstName(fullName);
                nameResponse.setLastName("");
                return nameResponse;
            }

            nameResponse.setFirstName(fullName.substring(0, splitIndex));
            nameResponse.setLastName(fullName.substring(splitIndex + 1));
            return nameResponse;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    public static class AddressResponse {
        private String addressId;
        private AddressType type;
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private boolean isDefault;

        public static AddressResponse from(Address address) {
            AddressResponse response = new AddressResponse();
            response.setAddressId(address.getAddressId());
            response.setType(address.getType());
            response.setLine1(address.getLine1());
            response.setLine2(address.getLine2());
            response.setCity(address.getCity());
            response.setState(address.getState());
            response.setPostalCode(address.getPostalCode());
            response.setCountry(address.getCountry());
            response.setIsDefault(address.isDefaultAddress());
            return response;
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

        public boolean isDefault() {
            return isDefault;
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

        public void setIsDefault(boolean isDefault) {
            this.isDefault = isDefault;
        }
    }
}
