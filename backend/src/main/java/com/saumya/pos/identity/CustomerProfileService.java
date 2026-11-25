package com.saumya.pos.identity;

import com.saumya.pos.identity.internal.CustomerProfile;
import com.saumya.pos.identity.internal.CustomerRepository;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CustomerProfileService {
    private final CustomerAccess access;
    private final CustomerRepository customers;
    public CustomerProfileService(CustomerAccess access, CustomerRepository customers) {
        this.access = access;
        this.customers = customers;
    }
    public record Profile(long id, String displayName, String email) {}
    public record Update(@Size(max=120) String displayName) {}
    @Transactional(readOnly=true)
    public Profile read(String username) {
        long id = access.resolve(username);
        CustomerProfile profile = customers.findById(id).orElseThrow(() -> unavailable());
        return new Profile(profile.id(), profile.displayName(), username);
    }
    @Transactional
    public Profile update(String username, Update update) {
        long id = access.resolve(username);
        access.lock(id);
        CustomerProfile profile = customers.findById(id).orElseThrow(() -> unavailable());
        String name = update.displayName() == null ? null : update.displayName().strip();
        if (name != null && name.isEmpty()) name = null;
        if (name != null && name.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name must be at most 120 characters");
        profile.displayName(name);
        customers.saveAndFlush(profile);
        return new Profile(profile.id(), profile.displayName(), username);
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer profile unavailable");
    }
}
