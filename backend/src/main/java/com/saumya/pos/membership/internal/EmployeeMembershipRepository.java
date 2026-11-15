package com.saumya.pos.membership.internal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmployeeMembershipRepository extends JpaRepository<EmployeeMembership,String>{
 java.util.Optional<EmployeeMembership> findByUsernameIgnoreCase(String username);
}
