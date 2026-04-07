package com.example.orderapi.dto;

import com.example.orderapi.domain.AddressType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CustomerRequest {
    @NotNull(message = "name is required")
    @Valid
    private NameRequest name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    @NotBlank(message = "phone is required")
    @Size(max = 30, message = "phone must be at most 30 characters")
    private String phone;

    @NotEmpty(message = "addresses are required")
    @Valid
    private List<AddressRequest> addresses;

    public NameRequest getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public List<AddressRequest> getAddresses() {
        return addresses;
    }

    public void setName(NameRequest name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setAddresses(List<AddressRequest> addresses) {
        this.addresses = addresses;
    }

    public static class NameRequest {
        @NotBlank(message = "name.firstName is required")
        private String firstName;

        @NotBlank(message = "name.lastName is required")
        private String lastName;

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

    public static class AddressRequest {
        @NotNull(message = "addresses.type is required")
        private AddressType type;

        @NotBlank(message = "addresses.line1 is required")
        private String line1;

        private String line2;

        @NotBlank(message = "addresses.city is required")
        private String city;

        @NotBlank(message = "addresses.state is required")
        private String state;

        @NotBlank(message = "addresses.postalCode is required")
        private String postalCode;

        @NotBlank(message = "addresses.country is required")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "addresses.country must be a 2-letter code")
        private String country;

        private Boolean isDefault = false;

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

        public Boolean getIsDefault() {
            return isDefault;
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

        public void setIsDefault(Boolean isDefault) {
            this.isDefault = isDefault;
        }
    }
}
