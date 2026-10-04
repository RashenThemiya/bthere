package com.jobhub.service.customer;

import com.jobhub.dto.customer.CustomerProfileRequest;
import com.jobhub.dto.customer.CustomerProfileResponse;
import com.jobhub.entity.customer.Customer;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.customer.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public CustomerProfileResponse get(Long userId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer profile has not been completed"
                ));
        return toResponse(customer);
    }

    @Transactional
    public CustomerProfileResponse upsert(Long userId, CustomerProfileRequest request) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseGet(() -> newCustomer(userId));

        customer.setFirstName(clean(request.firstName()));
        customer.setLastName(clean(request.lastName()));
        customer.setProfilePhoto(optional(request.profilePhoto()));
        customer.setAddressLine1(clean(request.addressLine1()));
        customer.setAddressLine2(optional(request.addressLine2()));
        customer.setCity(clean(request.city()));
        customer.setDistrict(optional(request.district()));
        customer.setProvince(optional(request.province()));
        customer.setPostalCode(optional(request.postalCode()));
        customer.setCountry(clean(request.country()));
        customer.setStatus("ACTIVE");
        return toResponse(customerRepository.save(customer));
    }

    private Customer newCustomer(Long userId) {
        Customer customer = new Customer();
        customer.setUserId(userId);
        return customer;
    }

    private CustomerProfileResponse toResponse(Customer customer) {
        return new CustomerProfileResponse(
                customer.getCustomerId(),
                customer.getUserId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getProfilePhoto(),
                customer.getAddressLine1(),
                customer.getAddressLine2(),
                customer.getCity(),
                customer.getDistrict(),
                customer.getProvince(),
                customer.getPostalCode(),
                customer.getCountry(),
                customer.getStatus(),
                true
        );
    }

    private String clean(String value) {
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
