package com.saumya.pos.warranty;

import com.saumya.pos.catalog.ObjectStoragePort;
import com.saumya.pos.checkout.OrderHistoryService;
import com.saumya.pos.security.AuthorizationContext;
import com.saumya.pos.service.ServiceSlaService;
import com.saumya.pos.shared.EventOutbox;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Tenant-owned claims against immutable warranty registrations. */
@Service
@Transactional
public class WarrantyClaimService {
    private static final int MAX_EVIDENCE=10*1024*1024;
    private static final Set<String> EVIDENCE_TYPES=Set.of("application/pdf","image/jpeg","image/png");
    private final JdbcTemplate db;
    private final AuthorizationContext authorization;
    private final OrderHistoryService orders;
    private final EventOutbox outbox;
    private final ObjectStoragePort storage;
    private final ServiceSlaService serviceSla;
    private final Clock clock;

    public WarrantyClaimService(JdbcTemplate db,AuthorizationContext authorization,OrderHistoryService orders,
            EventOutbox outbox,ObjectStoragePort storage,ServiceSlaService serviceSla,Clock clock){
        this.db=db;this.authorization=authorization;this.orders=orders;this.outbox=outbox;
        this.storage=storage;this.serviceSla=serviceSla;this.clock=clock;
    }

    public record Create(long warrantyId,String subject,String description){}
    public record Assignment(long expectedVersion,String assignedTo,String reason){}
    public record Inspection(long expectedVersion,String summary,String note,boolean customerVisible){}
    public record Decision(long expectedVersion,String decision,String resolutionType,String reason){}
    public record Resolution(long expectedVersion,String note){}
    public record Claim(long id,long warrantyId,String orderId,String productName,String subject,String description,
        String status,String assignedTo,String inspectionSummary,String decision,String decisionReason,
        String resolutionType,Instant submittedAt,Instant updatedAt,Instant resolvedAt,long version){}
    public record CustomerClaim(long id,long warrantyId,String orderId,String productName,String subject,
        String description,String status,String decision,String decisionReason,String resolutionType,
        Instant submittedAt,Instant updatedAt,Instant resolvedAt){}
    public record Event(long id,String eventType,String fromStatus,String toStatus,String note,
        boolean customerVisible,String actorType,String actorId,Instant occurredAt){}
    public record Evidence(String id,String fileName,String contentType,long byteSize,String sha256,
        boolean customerVisible,String uploaderType,Instant createdAt){}
    public record CustomerEvent(long id,String eventType,String fromStatus,String toStatus,String note,Instant occurredAt){}
    public record CustomerEvidence(String id,String fileName,String contentType,long byteSize,Instant createdAt){}
    public record VendorDetail(Claim claim,List<Event> events,List<Evidence> evidence){}
    public record CustomerDetail(CustomerClaim claim,List<CustomerEvent> events,List<CustomerEvidence> evidence){}
    public record EvidenceContent(String contentType,String fileName,byte[] content){}

    private AuthorizationContext.Context vendor(String permission){
        var actor=authorization.current();actor.requireVendorMembership();actor.requirePermission(permission);return actor;
    }
    private void customer(String username){
        var actor=authorization.current();
        if(!actor.isCustomer()||actor.isVendorEmployee()||actor.isPlatformUser()||!actor.username().equals(username))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated customer identity required");
    }
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Warranty claim unavailable");}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private String clean(String value,int max,String label){
        String s=value==null?"":value.strip();
        if(s.isEmpty()||s.length()>max||s.codePoints().anyMatch(Character::isISOControl))throw bad(label+" is invalid");
        return s;
    }
    private Instant instant(Object value){
        if(value instanceof Timestamp t)return t.toInstant();
        if(value instanceof Instant i)return i;
        throw new IllegalStateException("Unsupported claim timestamp");
    }

    public CustomerClaim submit(String username,Create input){
        customer(username);
        if(input==null||input.warrantyId()<=0)throw bad("Warranty claim details are required");
        String subject=clean(input.subject(),160,"Claim subject");
        String description=clean(input.description(),2000,"Claim description");
        for(var order:orders.customerPaidOnlineOrderReferences(username)){
            var rows=db.queryForList("""
                SELECT w.id,w.order_id,w.coverage_start_date,w.coverage_end_date,oi.name AS product_name
                FROM warranty_records w JOIN order_items oi
                  ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id AND oi.product_id=w.product_id
                WHERE w.tenant_id=? AND w.order_id=? AND w.id=?
                """,order.tenantId(),order.orderId(),input.warrantyId());
            if(rows.isEmpty())continue;
            var warranty=rows.getFirst();
            LocalDate start=((java.sql.Date)warranty.get("coverage_start_date")).toLocalDate();
            LocalDate end=((java.sql.Date)warranty.get("coverage_end_date")).toLocalDate();
            LocalDate today=LocalDate.now(clock);
            if(today.isBefore(start)||today.isAfter(end))throw conflict("Warranty coverage is not active for claim submission");
            Instant now=clock.instant();String claimKey=UUID.randomUUID().toString();
            db.update("""
                INSERT INTO warranty_claims(tenant_id,warranty_id,submitted_by,claim_key,subject,description,status,submitted_at,updated_at,version)
                VALUES(?,?,?,?,?,?,'SUBMITTED',?,?,0)
                """,order.tenantId(),input.warrantyId(),username,claimKey,subject,description,Timestamp.from(now),Timestamp.from(now));
            Long id=db.queryForObject("SELECT id FROM warranty_claims WHERE tenant_id=? AND claim_key=?",
                Long.class,order.tenantId(),claimKey);
            if(id==null)throw new IllegalStateException("Warranty claim ID was not generated");
            event(order.tenantId(),id,"CUSTOMER",username,"SUBMITTED",null,"SUBMITTED","Customer submitted warranty claim",true,now);
            publish(order.tenantId(),username,id,"WARRANTY_CLAIM.SUBMITTED","SUBMITTED",now);
            serviceSla.startForWarrantyClaim(order.tenantId(),id,username,now);
            return customerClaim(claim(load(order.tenantId(),id)));
        }
        throw missing();
    }

    @Transactional(readOnly=true)
    public List<Claim> vendorList(){
        var actor=vendor("SERVICE_VIEW");
        List<Map<String,Object>> rows;
        if(actor.hasPermission("SERVICE_ASSIGN")){
            rows=db.queryForList("""
                SELECT c.id,c.warranty_id,w.order_id,oi.name product_name,c.subject,c.description,c.status,c.assigned_to,
                  c.inspection_summary,c.decision,c.decision_reason,c.resolution_type,c.submitted_at,c.updated_at,c.resolved_at,c.version
                FROM warranty_claims c JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id AND oi.product_id=w.product_id
                WHERE c.tenant_id=? ORDER BY c.updated_at DESC,c.id DESC LIMIT 200
                """,actor.tenantId());
        }else{
            rows=db.queryForList("""
                SELECT c.id,c.warranty_id,w.order_id,oi.name product_name,c.subject,c.description,c.status,c.assigned_to,
                  c.inspection_summary,c.decision,c.decision_reason,c.resolution_type,c.submitted_at,c.updated_at,c.resolved_at,c.version
                FROM warranty_claims c JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id AND oi.product_id=w.product_id
                WHERE c.tenant_id=? AND c.assigned_to=? ORDER BY c.updated_at DESC,c.id DESC LIMIT 200
                """,actor.tenantId(),actor.username());
        }
        return rows.stream().map(this::claim).toList();
    }

    @Transactional(readOnly=true)
    public VendorDetail vendorGet(long id){
        var actor=vendor("SERVICE_VIEW");Map<String,Object> row=load(actor.tenantId(),id);
        scope(actor,(String)row.get("assigned_to"));
        return new VendorDetail(claim(row),events(actor.tenantId(),id,false),evidence(actor.tenantId(),id,false));
    }

    @Transactional(readOnly=true)
    public List<CustomerClaim> customerList(String username){
        customer(username);java.util.ArrayList<CustomerClaim> result=new java.util.ArrayList<>();
        for(var order:orders.customerPaidOnlineOrderReferences(username)){
            result.addAll(db.queryForList("""
                SELECT c.id,c.warranty_id,w.order_id,oi.name product_name,c.subject,c.description,c.status,
                  c.decision,c.decision_reason,c.resolution_type,c.submitted_at,c.updated_at,c.resolved_at,c.assigned_to,
                  c.inspection_summary,c.version
                FROM warranty_claims c JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id AND oi.product_id=w.product_id
                WHERE c.tenant_id=? AND w.order_id=? ORDER BY c.updated_at DESC,c.id DESC
                """,order.tenantId(),order.orderId()).stream().map(this::claim).map(this::customerClaim).toList());
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly=true)
    public CustomerDetail customerGet(String username,long id){
        customer(username);
        for(var order:orders.customerPaidOnlineOrderReferences(username)){
            var rows=db.queryForList("""
                SELECT c.id,c.warranty_id,w.order_id,oi.name product_name,c.subject,c.description,c.status,c.assigned_to,
                  c.inspection_summary,c.decision,c.decision_reason,c.resolution_type,c.submitted_at,c.updated_at,c.resolved_at,c.version
                FROM warranty_claims c JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id AND oi.product_id=w.product_id
                WHERE c.tenant_id=? AND w.order_id=? AND c.id=?
                """,order.tenantId(),order.orderId(),id);
            if(!rows.isEmpty())return new CustomerDetail(customerClaim(claim(rows.getFirst())),
                customerEvents(order.tenantId(),id),customerEvidence(order.tenantId(),id));
        }
        throw missing();
    }

    public Claim assign(long id,Assignment input){
        var actor=vendor("SERVICE_ASSIGN");
        if(id<=0||input==null)throw bad("Claim assignment is required");
        String assignee=clean(input.assignedTo(),100,"Assigned employee");
        String reason=clean(input.reason(),500,"Assignment reason");
        Map<String,Object> row=lock(actor.tenantId(),id);String current=(String)row.get("status");
        if(Set.of("REJECTED","RESOLVED").contains(current))throw conflict("A completed claim cannot be assigned");
        version(row,input.expectedVersion());
        Boolean active=db.queryForObject("""
            SELECT COUNT(*)>0 FROM employees e
            JOIN employee_role_assignments a ON a.tenant_id=e.tenant_id AND a.username=e.username AND a.active=TRUE
            JOIN vendor_roles r ON r.tenant_id=a.tenant_id AND r.id=a.role_id AND r.active=TRUE
            JOIN vendor_role_permissions p ON p.tenant_id=r.tenant_id AND p.role_id=r.id AND p.permission='SERVICE_VIEW'
            WHERE e.tenant_id=? AND e.username=? AND e.enabled=TRUE
            """,Boolean.class,actor.tenantId(),assignee);
        if(!Boolean.TRUE.equals(active))throw bad("Assignee must be an enabled service employee");
        Instant now=clock.instant();
        update(actor.tenantId(),id,input.expectedVersion(),"ASSIGNED",assignee,null,null,null,null,null,now);
        event(actor.tenantId(),id,"VENDOR",actor.username(),"ASSIGNED",current,"ASSIGNED",reason,true,now);
        publish(actor.tenantId(),actor.username(),id,"WARRANTY_CLAIM.ASSIGNED","ASSIGNED",now);
        serviceSla.recordFirstResponse(actor,"WARRANTY_CLAIM",id,now);
        return claim(load(actor.tenantId(),id));
    }

    public Claim inspect(long id,Inspection input){
        var actor=vendor("SERVICE_UPDATE");if(id<=0||input==null)throw bad("Inspection details are required");
        String summary=clean(input.summary(),2000,"Inspection summary"),note=clean(input.note(),1000,"Inspection note");
        Map<String,Object> row=lock(actor.tenantId(),id);scope(actor,(String)row.get("assigned_to"));
        version(row,input.expectedVersion());String current=(String)row.get("status");
        if(!Set.of("SUBMITTED","ASSIGNED","AWAITING_DECISION").contains(current))throw conflict("Inspection is not available in this claim state");
        Instant now=clock.instant();
        update(actor.tenantId(),id,input.expectedVersion(),"AWAITING_DECISION",(String)row.get("assigned_to"),summary,null,null,null,null,now);
        event(actor.tenantId(),id,"VENDOR",actor.username(),"INSPECTION_RECORDED",current,"AWAITING_DECISION",note,input.customerVisible(),now);
        publish(actor.tenantId(),actor.username(),id,"WARRANTY_CLAIM.INSPECTION_RECORDED","AWAITING_DECISION",now);
        serviceSla.recordFirstResponse(actor,"WARRANTY_CLAIM",id,now);
        return claim(load(actor.tenantId(),id));
    }

    public Claim decide(long id,Decision input){
        var actor=vendor("SERVICE_RESOLVE");if(id<=0||input==null)throw bad("Claim decision is required");
        String decision=norm(input.decision()),reason=clean(input.reason(),1000,"Decision reason");
        String resolution=input.resolutionType()==null?"":norm(input.resolutionType());
        if(!Set.of("APPROVED","REJECTED").contains(decision))throw bad("Decision must be approved or rejected");
        if("APPROVED".equals(decision)&&!Set.of("REPAIR","REPLACEMENT").contains(resolution))throw bad("Approved claims require repair or replacement");
        if("REJECTED".equals(decision)&&!resolution.isEmpty())throw bad("Rejected claims cannot have a resolution type");
        Map<String,Object> row=lock(actor.tenantId(),id);scope(actor,(String)row.get("assigned_to"));
        version(row,input.expectedVersion());
        if(!"AWAITING_DECISION".equals(row.get("status")))throw conflict("Claim must have an inspection before decision");
        String next="REJECTED".equals(decision)?"REJECTED":"REPAIR".equals(resolution)?"REPAIR_IN_PROGRESS":"REPLACEMENT_IN_PROGRESS";
        Instant now=clock.instant();
        update(actor.tenantId(),id,input.expectedVersion(),next,(String)row.get("assigned_to"),
            (String)row.get("inspection_summary"),decision,reason,"REJECTED".equals(decision)?null:resolution,null,now);
        event(actor.tenantId(),id,"VENDOR",actor.username(),"DECISION_RECORDED","AWAITING_DECISION",next,reason,true,now);
        publish(actor.tenantId(),actor.username(),id,"WARRANTY_CLAIM.DECISION_RECORDED",next,now);
        serviceSla.recordFirstResponse(actor,"WARRANTY_CLAIM",id,now);
        if ("REJECTED".equals(next)) serviceSla.closeCase(actor,"WARRANTY_CLAIM",id,now);
        return claim(load(actor.tenantId(),id));
    }

    public Claim resolve(long id,Resolution input){
        var actor=vendor("SERVICE_RESOLVE");if(id<=0||input==null)throw bad("Claim resolution is required");
        String note=clean(input.note(),1000,"Resolution note");
        Map<String,Object> row=lock(actor.tenantId(),id);scope(actor,(String)row.get("assigned_to"));
        version(row,input.expectedVersion());String current=(String)row.get("status");
        if(!Set.of("REPAIR_IN_PROGRESS","REPLACEMENT_IN_PROGRESS").contains(current))throw conflict("Only an approved repair or replacement can be resolved");
        Instant now=clock.instant();
        update(actor.tenantId(),id,input.expectedVersion(),"RESOLVED",(String)row.get("assigned_to"),
            (String)row.get("inspection_summary"),(String)row.get("decision"),(String)row.get("decision_reason"),
            (String)row.get("resolution_type"),Timestamp.from(now),now);
        event(actor.tenantId(),id,"VENDOR",actor.username(),"RESOLVED",current,"RESOLVED",note,true,now);
        publish(actor.tenantId(),actor.username(),id,"WARRANTY_CLAIM.RESOLVED","RESOLVED",now);
        serviceSla.recordFirstResponse(actor,"WARRANTY_CLAIM",id,now);
        serviceSla.closeCase(actor,"WARRANTY_CLAIM",id,now);
        return claim(load(actor.tenantId(),id));
    }

    public Evidence uploadCustomerEvidence(String username,long id,String fileName,String type,byte[] content){
        customer(username);Map<String,Object> claim=customerOwned(username,id);return upload(claim,username,"CUSTOMER",fileName,type,content,true);
    }
    public Evidence uploadVendorEvidence(long id,String fileName,String type,byte[] content,boolean visible){
        var actor=vendor("SERVICE_UPDATE");Map<String,Object> row=lock(actor.tenantId(),id);
        scope(actor,(String)row.get("assigned_to"));return upload(row,actor.username(),"VENDOR",fileName,type,content,visible);
    }

    private Evidence upload(Map<String,Object> row,String uploader,String uploaderType,String name,String type,byte[] bytes,boolean visible){
        String file=clean(name,255,"Evidence file name");
        if(Set.of("REJECTED","RESOLVED").contains((String)row.get("status")))throw conflict("Evidence cannot be added to a completed claim");
        if(file.contains("/")||file.contains("\\")||file.equals(".")||file.equals(".."))throw bad("Evidence file name is invalid");
        if(bytes==null||bytes.length==0||bytes.length>MAX_EVIDENCE)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Evidence must be between 1 byte and 10 MB");
        if(!EVIDENCE_TYPES.contains(type)||!validContent(type,bytes))throw bad("Evidence must be a valid PDF, JPEG or PNG");
        long tenant=((Number)row.get("tenant_id")).longValue(),id=((Number)row.get("id")).longValue();
        String evidenceId=UUID.randomUUID().toString(),key="tenants/"+tenant+"/warranty-claims/"+id+"/evidence/"+evidenceId;
        Instant now=clock.instant();String digest;
        try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception ex){throw new IllegalStateException(ex);}
        storage.put(key,bytes,type);
        try{
            db.update("""
                INSERT INTO warranty_claim_evidence(id,tenant_id,claim_id,object_key,original_file_name,content_type,byte_size,sha256,
                  uploaded_by,uploader_type,customer_visible,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """,evidenceId,tenant,id,key,file,type,bytes.length,digest,uploader,uploaderType,visible,Timestamp.from(now));
            event(tenant,id,uploaderType,uploader,"EVIDENCE_ADDED",(String)row.get("status"),(String)row.get("status"),
                "Evidence added: "+file,visible,now);
            publish(tenant,uploader,id,"WARRANTY_CLAIM.EVIDENCE_ADDED",(String)row.get("status"),now);
        }catch(RuntimeException ex){try{storage.delete(key);}catch(RuntimeException cleanup){ex.addSuppressed(cleanup);}throw ex;}
        return new Evidence(evidenceId,file,type,bytes.length,digest,visible,uploaderType,now);
    }

    @Transactional(readOnly=true)
    public EvidenceContent vendorEvidence(long claimId,String evidenceId){
        var actor=vendor("SERVICE_VIEW");Map<String,Object> claim=load(actor.tenantId(),claimId);scope(actor,(String)claim.get("assigned_to"));
        return evidenceContent(actor.tenantId(),claimId,evidenceId,false);
    }
    @Transactional(readOnly=true)
    public EvidenceContent customerEvidence(String username,long claimId,String evidenceId){
        customer(username);Map<String,Object> claim=customerOwned(username,claimId);
        return evidenceContent(((Number)claim.get("tenant_id")).longValue(),claimId,evidenceId,true);
    }
    private EvidenceContent evidenceContent(long tenant,long claim,String id,boolean customer){
        var rows=db.queryForList("""
            SELECT object_key,original_file_name,content_type,customer_visible FROM warranty_claim_evidence
            WHERE tenant_id=? AND claim_id=? AND id=?
            """,tenant,claim,id);
        if(rows.isEmpty()||(customer&&!Boolean.TRUE.equals(rows.getFirst().get("customer_visible"))))throw missing();
        var row=rows.getFirst();return new EvidenceContent((String)row.get("content_type"),
            (String)row.get("original_file_name"),storage.get((String)row.get("object_key")));
    }

    private Map<String,Object> customerOwned(String username,long id){
        for(var order:orders.customerPaidOnlineOrderReferences(username)){
            var rows=db.queryForList("""
                SELECT c.id,c.tenant_id,c.status,c.assigned_to FROM warranty_claims c
                JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
                WHERE c.tenant_id=? AND w.order_id=? AND c.id=?
                """,order.tenantId(),order.orderId(),id);
            if(!rows.isEmpty())return rows.getFirst();
        }
        throw missing();
    }
    private Map<String,Object> load(long tenant,long id){
        return db.queryForList("""
            SELECT c.id,c.tenant_id,c.warranty_id,w.order_id,oi.name product_name,c.subject,c.description,c.status,c.assigned_to,
              c.inspection_summary,c.decision,c.decision_reason,c.resolution_type,c.submitted_at,c.updated_at,c.resolved_at,c.version
            FROM warranty_claims c JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
            JOIN order_items oi ON oi.tenant_id=w.tenant_id AND oi.order_id=w.order_id AND oi.product_id=w.product_id
            WHERE c.tenant_id=? AND c.id=?
            """,tenant,id).stream().findFirst().orElseThrow(this::missing);
    }
    private Map<String,Object> lock(long tenant,long id){
        return db.queryForList("""
            SELECT c.id,c.tenant_id,c.warranty_id,w.order_id,c.status,c.assigned_to,c.inspection_summary,
              c.decision,c.decision_reason,c.resolution_type,c.version
            FROM warranty_claims c JOIN warranty_records w ON w.tenant_id=c.tenant_id AND w.id=c.warranty_id
            WHERE c.tenant_id=? AND c.id=? FOR UPDATE
            """,tenant,id).stream().findFirst().orElseThrow(this::missing);
    }
    private void scope(AuthorizationContext.Context actor,String assignee){
        if(!actor.hasPermission("SERVICE_ASSIGN")&&!actor.username().equals(assignee))throw missing();
    }
    private void version(Map<String,Object> row,long expected){
        if(((Number)row.get("version")).longValue()!=expected)throw conflict("Warranty claim changed; reload before saving");
    }
    private void update(long tenant,long id,long version,String status,String assignee,String inspection,
        String decision,String reason,String resolution,Timestamp resolved,Instant now){
        int changed=db.update("""
            UPDATE warranty_claims SET status=?,assigned_to=?,inspection_summary=?,decision=?,decision_reason=?,
              resolution_type=?,resolved_at=?,updated_at=?,version=version+1
            WHERE tenant_id=? AND id=? AND version=?
            """,status,assignee,inspection,decision,reason,resolution,resolved,Timestamp.from(now),tenant,id,version);
        if(changed!=1)throw conflict("Warranty claim changed; reload before saving");
    }
    private void event(long tenant,long id,String actorType,String actor,String type,String from,String to,String note,boolean visible,Instant now){
        db.update("""
            INSERT INTO warranty_claim_events(tenant_id,claim_id,actor_type,actor_id,event_type,from_status,to_status,note,customer_visible,occurred_at)
            VALUES(?,?,?,?,?,?,?,?,?,?)
            """,tenant,id,actorType,actor,type,from,to,note,visible,Timestamp.from(now));
    }
    private void publish(long tenant,String actor,long id,String type,String status,Instant now){
        outbox.append(tenant,actor,"WARRANTY_CLAIM",Long.toString(id),type,null,
            Map.of("claimId",id,"status",status,"occurredAt",now.toString()));
    }
    private List<Event> events(long tenant,long id,boolean customer){
        List<Map<String,Object>> rows;
        if(customer){
            rows=db.queryForList("""
                SELECT id,event_type,from_status,to_status,note,customer_visible,actor_type,actor_id,occurred_at
                FROM warranty_claim_events WHERE tenant_id=? AND claim_id=? AND customer_visible=TRUE
                ORDER BY occurred_at,id
                """,tenant,id);
        }else{
            rows=db.queryForList("""
                SELECT id,event_type,from_status,to_status,note,customer_visible,actor_type,actor_id,occurred_at
                FROM warranty_claim_events WHERE tenant_id=? AND claim_id=? ORDER BY occurred_at,id
                """,tenant,id);
        }
        return rows.stream().map(r->new Event(((Number)r.get("id")).longValue(),(String)r.get("event_type"),
            (String)r.get("from_status"),(String)r.get("to_status"),(String)r.get("note"),
            Boolean.TRUE.equals(r.get("customer_visible")),(String)r.get("actor_type"),(String)r.get("actor_id"),
            instant(r.get("occurred_at")))).toList();
    }

    private List<CustomerEvent> customerEvents(long tenant,long id){
        return events(tenant,id,true).stream().map(e->new CustomerEvent(e.id(),e.eventType(),e.fromStatus(),
            e.toStatus(),e.note(),e.occurredAt())).toList();
    }
    private List<CustomerEvidence> customerEvidence(long tenant,long id){
        return evidence(tenant,id,true).stream().map(e->new CustomerEvidence(e.id(),e.fileName(),
            e.contentType(),e.byteSize(),e.createdAt())).toList();
    }
    private List<Evidence> evidence(long tenant,long id,boolean customer){
        List<Map<String,Object>> rows;
        if(customer){
            rows=db.queryForList("""
                SELECT id,original_file_name,content_type,byte_size,sha256,customer_visible,uploader_type,created_at
                FROM warranty_claim_evidence WHERE tenant_id=? AND claim_id=? AND customer_visible=TRUE
                ORDER BY created_at,id
                """,tenant,id);
        }else{
            rows=db.queryForList("""
                SELECT id,original_file_name,content_type,byte_size,sha256,customer_visible,uploader_type,created_at
                FROM warranty_claim_evidence WHERE tenant_id=? AND claim_id=? ORDER BY created_at,id
                """,tenant,id);
        }
        return rows.stream().map(r->new Evidence((String)r.get("id"),(String)r.get("original_file_name"),
            (String)r.get("content_type"),((Number)r.get("byte_size")).longValue(),(String)r.get("sha256"),
            Boolean.TRUE.equals(r.get("customer_visible")),(String)r.get("uploader_type"),instant(r.get("created_at")))).toList();
    }

    private Claim claim(Map<String,Object> r){
        return new Claim(((Number)r.get("id")).longValue(),((Number)r.get("warranty_id")).longValue(),(String)r.get("order_id"),
            (String)r.get("product_name"),(String)r.get("subject"),(String)r.get("description"),(String)r.get("status"),
            (String)r.get("assigned_to"),(String)r.get("inspection_summary"),(String)r.get("decision"),(String)r.get("decision_reason"),
            (String)r.get("resolution_type"),instant(r.get("submitted_at")),instant(r.get("updated_at")),
            r.get("resolved_at")==null?null:instant(r.get("resolved_at")),((Number)r.get("version")).longValue());
    }
    private CustomerClaim customerClaim(Claim c){
        return new CustomerClaim(c.id(),c.warrantyId(),c.orderId(),c.productName(),c.subject(),c.description(),c.status(),
            c.decision(),c.decisionReason(),c.resolutionType(),c.submittedAt(),c.updatedAt(),c.resolvedAt());
    }
    private String norm(String value){return value==null?"":value.strip().toUpperCase(Locale.ROOT);}
    private boolean validContent(String type,byte[] b){
        if("application/pdf".equals(type))return b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-';
        if("image/jpeg".equals(type))return b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255;
        return b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10;
    }
}
