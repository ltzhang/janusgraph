package org.janusgraph.diskstorage.kvt;

import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.PermanentBackendException;
import org.janusgraph.diskstorage.BaseTransactionConfig;
import org.janusgraph.diskstorage.common.AbstractStoreTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KVTTransaction extends AbstractStoreTransaction {
    
    private static final Logger log = LoggerFactory.getLogger(KVTTransaction.class);
    
    private final KVTStoreManager manager;
    private final long transactionId;
    private volatile boolean isOpen;
    
    public KVTTransaction(KVTStoreManager manager, long transactionId, BaseTransactionConfig config) {
        super(config);
        this.manager = manager;
        this.transactionId = transactionId;
        this.isOpen = true;
        
        log.debug("Started KVT transaction with ID: {}", transactionId);
    }
    
    @Override
    public void commit() throws BackendException {
        if (!isOpen) {
            throw new IllegalStateException("Transaction is already closed");
        }
        
        try {
            boolean success = manager.commitTransaction(transactionId);
            if (!success) {
                throw new PermanentBackendException("Failed to commit KVT transaction: " + transactionId);
            }
            log.debug("Committed KVT transaction: {}", transactionId);
        } finally {
            isOpen = false;
        }
    }
    
    @Override
    public void rollback() throws BackendException {
        if (!isOpen) {
            // Already closed, nothing to rollback
            return;
        }
        
        try {
            boolean success = manager.rollbackTransaction(transactionId);
            if (!success) {
                log.warn("Failed to rollback KVT transaction: {}", transactionId);
            } else {
                log.debug("Rolled back KVT transaction: {}", transactionId);
            }
        } finally {
            isOpen = false;
        }
    }
    
    public long getTransactionId() {
        return transactionId;
    }
    
    public boolean isOpen() {
        return isOpen;
    }
    
    @Override
    public String toString() {
        return "KVTTransaction[id=" + transactionId + ", open=" + isOpen + "]";
    }
}