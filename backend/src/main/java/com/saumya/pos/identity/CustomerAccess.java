package com.saumya.pos.identity;
import com.saumya.pos.identity.internal.CustomerRepository;
import com.saumya.pos.security.AuthorizationContext;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class CustomerAccess {
    private final CustomerRepository customers;
    private final AuthorizationContext authorization;

    @Autowired
    public CustomerAccess(CustomerRepository customers, AuthorizationContext authorization){
        this.customers=customers;
        this.authorization=authorization;
    }

    /** Retained only for isolated subclasses in legacy unit fixtures; Spring always injects the guarded constructor. */
    public CustomerAccess(CustomerRepository customers){
        this(customers,null);
    }

    private AuthorizationContext.Context requireCustomer(String username){
        if(authorization==null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated customer context required");
        var actor=authorization.current();
        if(!actor.isCustomer()||!Objects.equals(actor.username(),username))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer identity does not match authenticated actor");
        return actor;
    }

    @Transactional(readOnly=true)
    public long resolve(String username){
        requireCustomer(username);
        return customers.resolve(username).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void lock(long id){
        if(authorization==null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated customer context required");
        var actor=authorization.current();
        if(!actor.isCustomer())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated customer required");
        long owner=customers.resolve(actor.username())
            .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
        if(owner!=id)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer resource unavailable");
        customers.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}
