package com.saumya.pos.documents;

import com.saumya.pos.marketplace.QuotationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Owns downloadable quotation copies; access control and audit are delegated to QuotationService. */
@Service
public class QuotationDocumentService {
    private final QuotationService quotations;
    private final QuotationPdfRenderer renderer;
    private final Clock clock;

    public QuotationDocumentService(QuotationService quotations,QuotationPdfRenderer renderer,Clock clock) {
        this.quotations=quotations;
        this.renderer=renderer;
        this.clock=clock;
    }

    public record Download(String reference,String filename,byte[] content) {}

    public Download vendor(long quotationId,int revisionNumber) {
        return generate(quotations.vendorDocument(quotationId,revisionNumber));
    }

    public Download customer(String username,long quotationId,int revisionNumber) {
        return generate(quotations.customerDocument(username,quotationId,revisionNumber));
    }

    private Download generate(QuotationService.DocumentData data) {
        String reference=UUID.randomUUID().toString();
        byte[] content=renderer.render(data,reference,clock.instant());
        if(content.length<5||content.length>2_000_000)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Quotation PDF exceeds the 2 MB document limit");
        String filename="quotation-"+data.quotationId()+"-revision-"+data.revisionNumber()+"-"+reference+".pdf";
        return new Download(reference,filename,content);
    }
}
