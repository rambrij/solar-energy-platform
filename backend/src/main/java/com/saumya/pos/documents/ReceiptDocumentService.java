package com.saumya.pos.documents;
import com.saumya.pos.documents.internal.*;
import com.saumya.pos.retail.PosService;
import java.time.Clock;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service @Transactional(isolation=Isolation.REPEATABLE_READ)
public class ReceiptDocumentService {
 private final PosService retail;private final ReceiptPdfRenderer renderer;private final ReceiptDocumentAuditRepository audits;private final Clock clock;
 public ReceiptDocumentService(PosService retail,ReceiptPdfRenderer renderer,ReceiptDocumentAuditRepository audits,Clock clock){this.retail=retail;this.renderer=renderer;this.audits=audits;this.clock=clock;}
 public record Download(String reference,String filename,byte[] content){}
 public Download generate(String username,String order,String requestedReason){
  if(order==null||!order.matches("[A-Za-z0-9-]{1,36}"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Receipt unavailable");
  if(requestedReason==null||requestedReason.isBlank()||requestedReason.length()>250||requestedReason.codePoints().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Document reason required (1-250 plain characters)");
  var scope=retail.scope(username);var data=ReceiptPdfData.saved(retail.receipt(scope,order));if(data.branch()!=scope.branch()||!data.orderId().equals(order))throw new ResponseStatusException(HttpStatus.FORBIDDEN);
  String reference=UUID.randomUUID().toString();var generated=clock.instant();byte[] bytes=renderer.render(data,reference,generated);if(bytes.length>2_000_000)throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt PDF exceeds size limit");
  String digest;try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  audits.saveAndFlush(new ReceiptDocumentAudit(reference,scope.tenant(),scope.branch(),order,scope.cashier(),requestedReason.strip(),digest,bytes.length,generated));
  return new Download(reference,"receipt-"+data.branch()+"-"+data.number()+"-copy-"+reference+".pdf",bytes);
 }
}
