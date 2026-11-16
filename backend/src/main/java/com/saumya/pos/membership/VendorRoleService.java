package com.saumya.pos.membership;

import com.saumya.pos.identity.IdentityProvisioningService;
import com.saumya.pos.security.AuthorizationContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class VendorRoleService {
    private final JdbcTemplate db;private final AuthorizationContext auth;private final IdentityProvisioningService invitations;
    public VendorRoleService(JdbcTemplate db,AuthorizationContext auth,IdentityProvisioningService invitations){this.db=db;this.auth=auth;this.invitations=invitations;}
    public record Role(long id,String key,String displayName,boolean active,long version,List<String> permissions){}
    public record Employee(String username,long branchId,boolean enabled,List<Long> roleIds,List<Long> registerIds){}
    public record EmployeeInvitation(String username,String invitationToken,String expiresAt,boolean accountAlreadyActive){}
    private AuthorizationContext.Context require(String permission){var c=auth.current();c.requireVendorMembership();c.requirePermission(permission);return c;}
    private String reason(String value){if(value==null||value.isBlank()||value.strip().length()>500)throw new ResponseStatusException(BAD_REQUEST,"A reason (1-500 characters) is required");return value.strip();}
    private List<String> permissions(List<String> input){
        if(input==null||input.isEmpty()||input.size()>100)throw new ResponseStatusException(BAD_REQUEST,"A role requires 1-100 permissions");
        TreeSet<String> result=new TreeSet<>();for(String p:input)if(!result.add(VendorPermissionRegistry.requireVendorPermission(p)))throw new ResponseStatusException(BAD_REQUEST,"Duplicate role permission");return List.copyOf(result);
    }
    private Map<String,Object> roleRow(AuthorizationContext.Context c,long id){
        return db.queryForList("SELECT id,role_key,display_name,active,version FROM vendor_roles WHERE tenant_id=? AND id=?",c.tenantId(),id)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Role unavailable"));
    }
    private Role view(AuthorizationContext.Context c,Map<String,Object> r){
        long id=((Number)r.get("id")).longValue();
        List<String> ps=db.queryForList("SELECT permission FROM vendor_role_permissions WHERE tenant_id=? AND role_id=? ORDER BY permission",String.class,c.tenantId(),id);
        return new Role(id,(String)r.get("role_key"),(String)r.get("display_name"),Boolean.TRUE.equals(r.get("active")),((Number)r.get("version")).longValue(),ps);
    }
    public List<Role> roles(){
        var c=require("ROLE_VIEW");
        return db.queryForList("SELECT id,role_key,display_name,active,version FROM vendor_roles WHERE tenant_id=? ORDER BY role_key",c.tenantId())
            .stream().map(r->view(c,r)).toList();
    }
    public Role role(long id){var c=require("ROLE_VIEW");return view(c,roleRow(c,id));}
    public Role create(String roleKey,String displayName,List<String> requested,String requestedReason){
        var c=require("ROLE_CREATE");String why=reason(requestedReason);
        String key=roleKey==null?"":roleKey.strip().toLowerCase(Locale.ROOT);
        String name=displayName==null?"":displayName.strip();
        if(!key.matches("[a-z][a-z0-9_-]{1,79}")||name.isBlank()||name.length()>120)throw new ResponseStatusException(BAD_REQUEST,"Valid role key and display name required");
        List<String> ps=permissions(requested);
        if(Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*)>0 FROM vendor_roles WHERE tenant_id=? AND role_key=?",Boolean.class,c.tenantId(),key)))
            throw new ResponseStatusException(CONFLICT,"Role key already exists");
        db.update("INSERT INTO vendor_roles(tenant_id,role_key,display_name,active,created_by,updated_by) VALUES(?,?,?,?,?,?)",
            c.tenantId(),key,name,true,c.username(),c.username());
        long id=db.queryForObject("SELECT id FROM vendor_roles WHERE tenant_id=? AND role_key=?",Long.class,c.tenantId(),key);
        for(String p:ps)db.update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",c.tenantId(),id,p,c.username());
        audit(c,null,"ROLE",Long.toString(id),"ROLE_CREATED",why);
        return role(id);
    }
    public Role update(long id,long expectedVersion,String displayName,List<String> requested,String requestedReason){
        var c=require("ROLE_UPDATE");String why=reason(requestedReason);var old=roleRow(c,id);
        List<String> ps=permissions(requested);String name=displayName==null?"":displayName.strip();
        if(name.isBlank()||name.length()>120)throw new ResponseStatusException(BAD_REQUEST,"Role display name required");
        int changed=db.update("UPDATE vendor_roles SET display_name=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE tenant_id=? AND id=? AND version=?",
            name,c.username(),c.tenantId(),id,expectedVersion);
        if(changed!=1)throw new ResponseStatusException(CONFLICT,"Role changed; reload before updating");
        db.update("DELETE FROM vendor_role_permissions WHERE tenant_id=? AND role_id=?",c.tenantId(),id);
        for(String p:ps)db.update("INSERT INTO vendor_role_permissions(tenant_id,role_id,permission,created_by) VALUES(?,?,?,?)",c.tenantId(),id,p,c.username());
        audit(c,null,"ROLE",Long.toString(id),"ROLE_UPDATED",why);
        audit(c,null,"ROLE_PERMISSIONS_REPLACED",Long.toString(id),"ROLE_PERMISSIONS_CHANGED",why);
        return role(id);
    }
    public void deactivate(long id,String requestedReason){
        var c=require("ROLE_UPDATE");String why=reason(requestedReason);roleRow(c,id);
        db.update("UPDATE vendor_roles SET active=FALSE,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE tenant_id=? AND id=?",c.username(),c.tenantId(),id);
        audit(c,null,"ROLE",Long.toString(id),"ROLE_DEACTIVATED",why);
    }
    public List<Employee> employees(){
        var c=require("USER_VIEW");
        var names=db.queryForList("SELECT username,branch_id,enabled FROM employees WHERE tenant_id=? ORDER BY username",c.tenantId());
        return names.stream().map(r->employee(c,(String)r.get("username"),((Number)r.get("branch_id")).longValue(),Boolean.TRUE.equals(r.get("enabled")))).toList();
    }
    private Employee employee(AuthorizationContext.Context c,String username,long branch,boolean enabled){
        var roles=db.queryForList("SELECT role_id FROM employee_role_assignments WHERE tenant_id=? AND username=? AND active=TRUE ORDER BY role_id",Long.class,c.tenantId(),username);
        var registers=db.queryForList("SELECT register_id FROM employee_register_assignments WHERE tenant_id=? AND username=? AND active=TRUE ORDER BY register_id",Long.class,c.tenantId(),username);
        return new Employee(username,branch,enabled,roles,registers);
    }
    public EmployeeInvitation createEmployee(String username,long branchId,long roleId,String requestedReason){
        var c=require("USER_CREATE");String why=reason(requestedReason);String normalized=username==null?"":username.strip().toLowerCase(Locale.ROOT);
        if(!normalized.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||normalized.length()>100)throw new ResponseStatusException(BAD_REQUEST,"Valid employee email required");
        var role=roleRow(c,roleId);if(!Boolean.TRUE.equals(role.get("active")))throw new ResponseStatusException(CONFLICT,"Inactive role cannot be assigned");
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*)>0 FROM branches WHERE tenant_id=? AND id=?",Boolean.class,c.tenantId(),branchId)))
            throw new ResponseStatusException(NOT_FOUND,"Branch unavailable");
        if(Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*)>0 FROM employees WHERE tenant_id=? AND username=?",Boolean.class,c.tenantId(),normalized)))
            throw new ResponseStatusException(CONFLICT,"Employee membership already exists");
        var invitation=invitations.issueInvitation(normalized);
        db.update("INSERT INTO employees(username,tenant_id,branch_id,enabled) VALUES(?,?,?,TRUE)",normalized,c.tenantId(),branchId);
        db.update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",
            c.tenantId(),branchId,normalized,roleId,c.username());
        audit(c,normalized,"EMPLOYEE",normalized,"EMPLOYEE_CREATED",why);
        audit(c,normalized,"ROLE_ASSIGNMENT",Long.toString(roleId),"ROLE_ASSIGNED",why);
        return new EmployeeInvitation(normalized,invitation.token(),invitation.expiresAt()==null?null:invitation.expiresAt().toString(),invitation.accountAlreadyActive());
    }
    public Employee assignRoles(String username,List<Long> roleIds,String requestedReason){
        var c=require("ROLE_ASSIGN");String why=reason(requestedReason);String subject=subject(c,username);
        Map<String,Object> emp=employeeRow(c,subject);long branch=((Number)emp.get("branch_id")).longValue();
        List<Long> selected=roleIds==null?List.of():roleIds.stream().distinct().sorted().toList();
        if(selected.size()>50)throw new ResponseStatusException(BAD_REQUEST,"At most 50 roles may be assigned");
        for(long id:selected){var role=roleRow(c,id);if(!Boolean.TRUE.equals(role.get("active")))throw new ResponseStatusException(CONFLICT,"Inactive role cannot be assigned");}
        db.update("UPDATE employee_role_assignments SET active=FALSE,revoked_by=?,revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND username=? AND active=TRUE",
            c.username(),c.tenantId(),subject);
        for(long id:selected){
            int exists=db.update("UPDATE employee_role_assignments SET branch_id=?,active=TRUE,assigned_by=?,assigned_at=CURRENT_TIMESTAMP,revoked_by=NULL,revoked_at=NULL WHERE tenant_id=? AND username=? AND role_id=?",
                branch,c.username(),c.tenantId(),subject,id);
            if(exists==0)db.update("INSERT INTO employee_role_assignments(tenant_id,branch_id,username,role_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",c.tenantId(),branch,subject,id,c.username());
            audit(c,subject,"ROLE_ASSIGNMENT",Long.toString(id),"ROLE_ASSIGNED",why);
        }
        audit(c,subject,"ROLE_ASSIGNMENT",subject,"ROLE_ASSIGNMENTS_REPLACED",why);
        return employee(c,subject,branch,Boolean.TRUE.equals(emp.get("enabled")));
    }
    public Employee assignRegisters(String username,List<Long> registerIds,String requestedReason){
        var c=require("ROLE_ASSIGN");String why=reason(requestedReason);String subject=subject(c,username);var emp=employeeRow(c,subject);
        long branch=((Number)emp.get("branch_id")).longValue();List<Long> selected=registerIds==null?List.of():registerIds.stream().distinct().sorted().toList();
        if(selected.size()>100)throw new ResponseStatusException(BAD_REQUEST,"At most 100 registers may be assigned");
        for(long id:selected)if(!Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*)>0 FROM registers WHERE tenant_id=? AND branch_id=? AND id=?",Boolean.class,c.tenantId(),branch,id)))
            throw new ResponseStatusException(NOT_FOUND,"Register unavailable in employee branch");
        db.update("UPDATE employee_register_assignments SET active=FALSE,revoked_by=?,revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND username=? AND active=TRUE",
            c.username(),c.tenantId(),subject);
        for(long id:selected){
            int updated=db.update("UPDATE employee_register_assignments SET active=TRUE,assigned_by=?,assigned_at=CURRENT_TIMESTAMP,revoked_by=NULL,revoked_at=NULL WHERE tenant_id=? AND username=? AND register_id=?",
                c.username(),c.tenantId(),subject,id);
            if(updated==0)db.update("INSERT INTO employee_register_assignments(tenant_id,branch_id,username,register_id,active,assigned_by) VALUES(?,?,?,?,TRUE,?)",
                c.tenantId(),branch,subject,id,c.username());
            audit(c,subject,"REGISTER_ASSIGNMENT",Long.toString(id),"REGISTER_ASSIGNED",why);
        }
        audit(c,subject,"REGISTER_ASSIGNMENT",subject,"REGISTER_ASSIGNMENTS_REPLACED",why);
        return employee(c,subject,branch,Boolean.TRUE.equals(emp.get("enabled")));
    }
    public Employee updateBranch(String username,long newBranch,String requestedReason){
        var c=require("USER_UPDATE");String why=reason(requestedReason);String subject=subject(c,username);var emp=employeeRow(c,subject);
        if(!Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*)>0 FROM branches WHERE tenant_id=? AND id=?",Boolean.class,c.tenantId(),newBranch)))
            throw new ResponseStatusException(NOT_FOUND,"Branch unavailable");
        long oldBranch=((Number)emp.get("branch_id")).longValue();
        if(oldBranch==newBranch)return employee(c,subject,oldBranch,Boolean.TRUE.equals(emp.get("enabled")));
        db.update("DELETE FROM employee_register_assignments WHERE tenant_id=? AND username=?",c.tenantId(),subject);
        db.update("UPDATE employee_role_assignments SET branch_id=? WHERE tenant_id=? AND username=?",newBranch,c.tenantId(),subject);
        db.update("UPDATE employees SET branch_id=? WHERE tenant_id=? AND username=?",newBranch,c.tenantId(),subject);
        audit(c,subject,"EMPLOYEE",subject,"EMPLOYEE_BRANCH_CHANGED",why);
        audit(c,subject,"REGISTER_ASSIGNMENT",subject,"REGISTER_ASSIGNMENTS_REVOKED_ON_BRANCH_CHANGE",why);
        return employee(c,subject,newBranch,Boolean.TRUE.equals(emp.get("enabled")));
    }
    public void disableEmployee(String username,String requestedReason){
        var c=require("USER_DISABLE");String why=reason(requestedReason);String subject=subject(c,username);
        if(subject.equals(c.username()))throw new ResponseStatusException(CONFLICT,"An administrator cannot disable their own membership");
        var emp=employeeRow(c,subject);
        db.update("UPDATE employees SET enabled=FALSE WHERE tenant_id=? AND username=?",c.tenantId(),subject);
        db.update("UPDATE employee_role_assignments SET active=FALSE,revoked_by=?,revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND username=? AND active=TRUE",c.username(),c.tenantId(),subject);
        db.update("UPDATE employee_register_assignments SET active=FALSE,revoked_by=?,revoked_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND username=? AND active=TRUE",c.username(),c.tenantId(),subject);
        audit(c,subject,"EMPLOYEE",subject,"EMPLOYEE_DISABLED",why);
    }
    public List<String> effectivePermissions(String username){
        var c=require("USER_VIEW");String subject=subject(c,username);
        var emp=employeeRow(c,subject);
        if(!Boolean.TRUE.equals(emp.get("enabled")))return List.of();
        return db.queryForList("""
            SELECT DISTINCT rp.permission FROM employee_role_assignments a
            JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id
            JOIN vendor_role_permissions rp ON rp.tenant_id=r.tenant_id AND rp.role_id=r.id
            WHERE a.tenant_id=? AND a.username=? AND a.active=TRUE AND r.active=TRUE
            ORDER BY rp.permission
            """,String.class,c.tenantId(),subject);
    }
    private String subject(AuthorizationContext.Context c,String value){
        String s=value==null?"":value.strip().toLowerCase(Locale.ROOT);
        if(!s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||s.length()>100)throw new ResponseStatusException(BAD_REQUEST,"Invalid employee identity");
        return s;
    }
    private Map<String,Object> employeeRow(AuthorizationContext.Context c,String username){
        return db.queryForList("SELECT username,branch_id,enabled FROM employees WHERE tenant_id=? AND username=?",c.tenantId(),username)
            .stream().findFirst().orElseThrow(()->new ResponseStatusException(NOT_FOUND,"Employee unavailable"));
    }
    private void audit(AuthorizationContext.Context c,String subject,String type,String id,String action,String reason){
        db.update("INSERT INTO vendor_access_audit_events(tenant_id,actor_username,subject_username,resource_type,resource_id,action,reason) VALUES(?,?,?,?,?,?,?)",
            c.tenantId(),c.username(),subject,type,id,action,reason);
    }
}
