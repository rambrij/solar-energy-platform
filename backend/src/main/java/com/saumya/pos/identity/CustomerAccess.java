package com.saumya.pos.identity;
import com.saumya.pos.identity.internal.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class CustomerAccess {
    private final CustomerRepository customers;
    public CustomerAccess(CustomerRepository customers){this.customers=customers;}
    @Transactional(readOnly=true) public long resolve(String username){return customers.resolve(username).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));}
    @Transactional(propagation=Propagation.MANDATORY) public void lock(long id){customers.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));}
}
