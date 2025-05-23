/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public class CachingFileSystemLocationSnapshotHasher implements FileSystemLocationSnapshotHasher {
    private final FileSystemLocationSnapshotHasher delegate;
    private final ResourceSnapshotterCacheService resourceSnapshotterCacheService;
    private final HashCode delegateConfigurationHash;

    public CachingFileSystemLocationSnapshotHasher(FileSystemLocationSnapshotHasher delegate, ResourceSnapshotterCacheService resourceSnapshotterCacheService) {
        this.delegate = delegate;
        this.resourceSnapshotterCacheService = resourceSnapshotterCacheService;
        Hasher hasher = Hashing.newHasher();
        appendConfigurationToHasher(hasher);
        this.delegateConfigurationHash = hasher.hash();
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
    }

    @Nullable
    @Override
    public HashCode hash(FileSystemLocationSnapshot snapshot) throws IOException {
        return resourceSnapshotterCacheService.hashFile(snapshot, delegate, delegateConfigurationHash);
    }
}
