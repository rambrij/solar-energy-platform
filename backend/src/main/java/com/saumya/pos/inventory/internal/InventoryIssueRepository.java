package com.saumya.pos.inventory.internal;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InventoryIssueRepository extends JpaRepository<InventoryIssue,InventoryIssue.Key> {}
