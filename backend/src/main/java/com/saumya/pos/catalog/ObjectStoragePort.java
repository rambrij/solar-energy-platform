package com.saumya.pos.catalog;

public interface ObjectStoragePort {
    void put(String objectKey,byte[] content,String contentType);
    byte[] get(String objectKey);
    void delete(String objectKey);
}
