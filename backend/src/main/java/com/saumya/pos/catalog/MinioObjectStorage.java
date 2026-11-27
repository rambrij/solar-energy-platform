package com.saumya.pos.catalog;

import io.minio.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MinioObjectStorage implements ObjectStoragePort {
    private static final String ALLOWED_KEY="(?:tenants/[1-9][0-9]*/products/[1-9][0-9]*/images/[0-9a-f-]{36}|tenants/[1-9][0-9]*/warranty-claims/[1-9][0-9]*/evidence/[0-9a-f-]{36})";
    private final String endpoint,accessKey,secretKey,bucket;
    private volatile MinioClient client;

    public MinioObjectStorage(
        @Value("${app.object-storage.endpoint:}") String endpoint,
        @Value("${app.object-storage.access-key:}") String accessKey,
        @Value("${app.object-storage.secret-key:}") String secretKey,
        @Value("${app.object-storage.bucket:saumya-assets}") String bucket) {
        this.endpoint=endpoint;this.accessKey=accessKey;this.secretKey=secretKey;this.bucket=bucket;
    }

    private MinioClient client() {
        if(endpoint==null||endpoint.isBlank()||accessKey==null||accessKey.isBlank()||
           secretKey==null||secretKey.isBlank()||bucket==null||!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Object storage is not configured");
        MinioClient current=client;
        if(current==null) {
            synchronized(this) {
                current=client;
                if(current==null) {
                    try { current=MinioClient.builder().endpoint(endpoint).credentials(accessKey,secretKey).build(); }
                    catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Object storage configuration is invalid",ex);}
                    client=current;
                }
            }
        }
        return current;
    }

    private void ensureBucket(MinioClient target) throws Exception {
        if(!target.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            try { target.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()); }
            catch(Exception race) {
                if(!target.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))throw race;
            }
        }
    }

    @Override public void put(String key,byte[] content,String contentType) {
        if(key==null||!key.matches(ALLOWED_KEY)||
           content==null||content.length==0||contentType==null)
            throw new IllegalArgumentException("Tenant-scoped image object material required");
        try {
            MinioClient target=client();ensureBucket(target);
            target.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(new ByteArrayInputStream(content),content.length,-1)
                .contentType(contentType).build());
        } catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product image storage is unavailable",ex);}
    }

    @Override public byte[] get(String key) {
        if(key==null||!key.matches("tenants/[1-9][0-9]*/products/[1-9][0-9]*/images/[0-9a-f-]{36}"))
            throw new IllegalArgumentException("Tenant-scoped image key required");
        try(InputStream stream=client().getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            byte[] bytes=stream.readAllBytes();
            if(bytes.length>10*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Stored object exceeds the configured limit");
            return bytes;
        } catch(ResponseStatusException ex){throw ex;}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product image storage is unavailable",ex);}
    }

    @Override public void delete(String key) {
        if(key==null||!key.matches("tenants/[1-9][0-9]*/products/[1-9][0-9]*/images/[0-9a-f-]{36}"))
            throw new IllegalArgumentException("Tenant-scoped image key required");
        try {client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());}
        catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Product image storage is unavailable",ex);}
    }
}
