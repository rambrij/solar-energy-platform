package com.saumya.pos.customer.internal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VendorCustomerRepository extends JpaRepository<VendorCustomer,String>{boolean existsByTenantAndCustomer(long tenant,long customer);}
