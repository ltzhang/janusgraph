// Copyright 2024 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.janusgraph.diskstorage.kvt;

import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStoreTest;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import org.janusgraph.diskstorage.keycolumnvalue.StoreFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for KVTStoreManager.
 * 
 * This class extends the standard JanusGraph store tests to verify
 * that the KVT backend behaves correctly.
 */
public class KVTStoreManagerTest extends KeyColumnValueStoreTest {

    private KVTStoreManager manager;

    @Override
    public KeyColumnValueStoreManager openStorageManager() throws BackendException {
        // Skip tests if native library is not available
        try {
            manager = new KVTStoreManager();
            return manager;
        } catch (RuntimeException e) {
            if (e.getMessage().contains("native library")) {
                Assumptions.assumeTrue(false, "KVT native library not available: " + e.getMessage());
            }
            throw e;
        }
    }

    @BeforeEach
    public void setUp() {
        // Additional setup if needed
    }

    @AfterEach 
    public void tearDown() throws Exception {
        if (manager != null) {
            try {
                manager.close();
            } catch (Exception e) {
                // Ignore errors during cleanup
            }
        }
    }

    @Test
    public void testKVTSpecificFeatures() throws BackendException {
        KVTStoreManager kvtManager = (KVTStoreManager) manager;
        
        StoreFeatures features = kvtManager.getFeatures();
        
        // Verify KVT-specific feature set
        assertTrue(features.hasOrderedScan(), "KVT should support ordered scan");
        assertTrue(features.hasUnorderedScan(), "KVT should support unordered scan"); 
        assertTrue(features.isKeyOrdered(), "KVT should have ordered keys");
        assertFalse(features.isPersistent(), "KVT is in-memory, should not be persistent");
        assertTrue(features.isTransactional(), "KVT should be transactional");
        assertTrue(features.supportsBatchMutation(), "KVT should support batch mutations");
        
        assertEquals("kvt", kvtManager.getName(), "Store manager name should be 'kvt'");
    }

    @Test
    public void testStorageLifecycle() throws BackendException {
        KVTStoreManager kvtManager = (KVTStoreManager) manager;
        
        // Initially should not exist (no stores created)
        assertFalse(kvtManager.exists(), "Should not exist initially");
        
        // Create a store
        kvtManager.openDatabase("test_store");
        assertTrue(kvtManager.exists(), "Should exist after creating a store");
        
        // Clear storage
        kvtManager.clearStorage();
        // Note: exists() behavior after clearStorage() depends on implementation
        
        // Close should work without errors
        kvtManager.close();
    }
}