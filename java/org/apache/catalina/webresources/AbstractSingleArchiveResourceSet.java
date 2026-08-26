/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.webresources;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResourceRoot;
import org.apache.tomcat.util.buf.UriUtil;

/**
 * Base class for a {@link org.apache.catalina.WebResourceSet} based on a single, rather than nested, archive.
 */
public abstract class AbstractSingleArchiveResourceSet extends AbstractArchiveResourceSet {

    private volatile Boolean multiRelease;

    private static final boolean CACHE_ARCHIVE_ENTRIES = Boolean.valueOf(System.getProperty("tomcat.cacheArchiveEntries"));

    static {
        System.out.println("Tomcat Cache Archive Entries [" + CACHE_ARCHIVE_ENTRIES + "]");
    }

    /**
     * A no argument constructor is required for this to work with the digester.
     */
    public AbstractSingleArchiveResourceSet() {
    }


    /**
     * Constructs a new {@code AbstractSingleArchiveResourceSet}.
     *
     * @param root         The root
     * @param webAppMount  The web app mount
     * @param base         The base
     * @param internalPath The internal path
     *
     * @throws IllegalStateException if the {@link WebResourceRoot} is available but this resource set cannot be
     *                                      started
     */
    public AbstractSingleArchiveResourceSet(WebResourceRoot root, String webAppMount, String base, String internalPath)
            throws IllegalStateException {
        setRoot(root);
        setWebAppMount(webAppMount);
        setBase(base);
        setInternalPath(internalPath);

        if (isCacheArchiveEntries()) {
            archiveEntries = loadArchiveEntries();
        }

        if (getRoot().getState().isAvailable()) {
            try {
                start();
            } catch (LifecycleException e) {
                throw new IllegalStateException(sm.getString("abstractSingleArchiveResourceSet.startFail"), e);
            }
        }
    }

    @Override
    protected boolean isCacheArchiveEntries() {
        return CACHE_ARCHIVE_ENTRIES;
    }

    private HashMap<String, JarEntry> loadArchiveEntries() {
        JarFile jarFile = null;
        try {
            HashMap<String, JarEntry> contents = new HashMap<>();
            jarFile = openJarFile();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                contents.put(entry.getName(), entry);
            }
            return contents;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            if (jarFile != null) {
                closeJarFile();
            }
        }
    }

    @Override
    protected Map<String, JarEntry> getArchiveEntries(boolean single) {
        if (isCacheArchiveEntries() && archiveEntries != null) {
            return archiveEntries;
        }
        synchronized (archiveLock) {
            if (archiveEntries == null && !single) {
                archiveEntries = loadArchiveEntries();
            }
            return archiveEntries;
        }
    }


    @Override
    protected JarEntry getArchiveEntry(String pathInArchive) {
        JarFile jarFile = null;
        try {
            jarFile = openJarFile();
            return jarFile.getJarEntry(pathInArchive);
        } catch (IOException ioe) {
            // Should never happen
            throw new IllegalStateException(
                    sm.getString("abstractArchiveResourceSet.archiveEntryFail", getBase()), ioe);
        } finally {
            if (jarFile != null) {
                closeJarFile();
            }
        }
    }


    @Override
    protected boolean isMultiRelease() {
        if (multiRelease == null) {
            synchronized (archiveLock) {
                if (multiRelease == null) {
                    JarFile jarFile = null;
                    try {
                        jarFile = openJarFile();
                        multiRelease = Boolean.valueOf(jarFile.isMultiRelease());
                    } catch (IOException ioe) {
                        // Should never happen
                        throw new IllegalStateException(
                                sm.getString("abstractArchiveResourceSet.multiReleaseFail", getBase()), ioe);
                    } finally {
                        if (jarFile != null) {
                            closeJarFile();
                        }
                    }
                }
            }
        }

        return multiRelease.booleanValue();
    }


    // -------------------------------------------------------- Lifecycle methods
    @Override
    protected void initInternal() throws LifecycleException {

        try (JarFile jarFile = new JarFile(new File(getBase()), true, ZipFile.OPEN_READ, Runtime.version())) {
            setManifest(jarFile.getManifest());
        } catch (IOException ioe) {
            throw new LifecycleException(
                    sm.getString("abstractArchiveResourceSet.manifestFail", getBase()), ioe);
        }

        try {
            setBaseUrl(UriUtil.buildJarSafeUrl(new File(getBase())));
        } catch (IOException ioe) {
            throw new LifecycleException(
                    sm.getString("abstractArchiveResourceSet.baseUrlFail", getBase()), ioe);
        }
    }
}
