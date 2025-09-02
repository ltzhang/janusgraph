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
import org.junit.jupiter.api.Assumptions;

/**
 * Test suite for KVTKeyColumnValueStore.
 * 
 * This class runs all the standard JanusGraph key-column-value store tests
 * against the KVT backend to ensure compatibility.
 */
public class KVTKeyColumnValueStoreTest extends KeyColumnValueStoreTest {

    @Override
    public KeyColumnValueStoreManager openStorageManager() throws BackendException {
        // Skip tests if native library is not available
        try {
            return new KVTStoreManager();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("native library")) {
                Assumptions.assumeTrue(false, "KVT native library not available: " + e.getMessage());
            }
            throw e;
        }
    }
}