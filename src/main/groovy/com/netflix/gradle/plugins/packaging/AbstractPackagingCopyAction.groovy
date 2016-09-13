/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.gradle.plugins.packaging

import org.gradle.api.internal.file.CopyActionProcessingStreamAction
import org.gradle.api.internal.file.copy.*
import org.gradle.api.internal.tasks.SimpleWorkResult
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.UncheckedException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Field

public abstract class AbstractPackagingCopyAction<T extends SystemPackagingTask> implements CopyAction {
    static final Logger logger = LoggerFactory.getLogger(AbstractPackagingCopyAction.class)

    T task
    File tempDir
    Collection<File> filteredFiles = []

    protected AbstractPackagingCopyAction(T task) {
        this.task = task
    }

    public WorkResult execute(CopyActionProcessingStream stream) {
        try {
            startVisit(this)
            stream.process(new StreamAction());
            endVisit()
        } catch (Exception e) {
            UncheckedException.throwAsUncheckedException(e);
        }

        return new SimpleWorkResult(true);
    }

    // Not a static class
    private class StreamAction implements CopyActionProcessingStreamAction {
        public void processFile(FileCopyDetailsInternal details) {
            // While decoupling the spec from the action is nice, it contains some needed info
            def ourSpec = extractSpec(details) // Can be null
            if (details.isDirectory()) {
                visitDir(details, ourSpec);
            } else {
                visitFile(details, ourSpec);
            }
        }
    }

    protected abstract void visitDir(FileCopyDetailsInternal dirDetails, def specToLookAt)
    protected abstract void visitFile(FileCopyDetailsInternal fileDetails, def specToLookAt)
    protected abstract void addLink(Link link)
    protected abstract void addDependency(Dependency dependency)
    protected abstract void addConflict(Dependency dependency)
    protected abstract void addObsolete(Dependency dependency)
    protected abstract void addProvides(Dependency dependency)
    protected abstract void addDirectory(Directory directory)
    protected abstract void end()

    void startVisit(CopyAction action) {
        // Delay reading destinationDir until we start executing
        tempDir = task.getTemporaryDir()
    }

    void endVisit() {
        for (Link link : task.getAllLinks()) {
            logger.debug "adding link {} -> {}", link.path, link.target
            addLink link
        }

        for (Dependency dep : task.getAllDependencies()) {
            logger.debug "adding dependency on {} {}", dep.packageName, dep.version
            addDependency dep
        }

        for (Dependency obsolete: task.getAllObsoletes()) {
            logger.debug "adding obsoletes on {} {}", obsolete.packageName, obsolete.version
            addObsolete obsolete
        } 

        for (Dependency conflict : task.getAllConflicts()) {
            logger.debug "adding conflicts on {} {}", conflict.packageName, conflict.version
            addConflict conflict
        }

        for (Dependency provides : task.getAllProvides()) {
            logger.debug "adding provides on {} {}", provides.packageName, provides.version
            addProvides(provides)
        }

        task.directories.each { directory ->
            logger.debug "adding directory {}", directory.path
            addDirectory(directory)
        }

        end()

        // TODO Clean up filteredFiles

        // TODO Investigate, we seem to always set to true.
    }

    String concat(Collection<Object> scripts) {
        String shebang
        StringBuilder result = new StringBuilder();
        scripts.each { script ->
            script?.eachLine { line ->
                if (line.matches('^#!.*$')) {
                    if (!shebang) {
                        shebang = line
                    } else if (line != shebang) {
                        throw new IllegalArgumentException("mismatching #! script lines")
                    }
                } else {
                    result.append line
                    result.append "\n"
                }
            }
        }
        if (shebang) {
            result.insert(0, shebang + "\n")
        }
        result.toString()
    }

    CopySpecInternal extractSpec(FileCopyDetailsInternal fileDetails) {
        if (fileDetails instanceof DefaultFileCopyDetails) {
            def startingClass = fileDetails.getClass() // It's in there somewhere
            while( startingClass != null && startingClass != DefaultFileCopyDetails) {
                startingClass = startingClass.superclass
            }
            Field specField = startingClass.getDeclaredField('specResolver')
            specField.setAccessible(true)
            CopySpecResolver specResolver = specField.get(fileDetails)

            Field field = DefaultCopySpec.DefaultCopySpecResolver.class.getDeclaredField('this$0')
            field.setAccessible(true)
            CopySpecInternal spec = field.get(specResolver)
            return spec
        } else {
            return null
        }
    }
    /**
     * Look at FileDetails to get a file. If it's filtered file, we need to write it out to the filesystem ourselves.
     * Issue #30, FileVisitDetailsImpl won't give us file, since it filters on the fly.
     */
    File extractDir(FileCopyDetailsInternal fileDetails) {
        File outputDir
        try {
            outputDir = fileDetails.getFile()
        } catch (UnsupportedOperationException uoe) {
            // Can't access MappingCopySpecVisitor.FileVisitDetailsImpl since it's private, so we have to probe. We would test this:
            // if (fileDetails instanceof MappingCopySpecVisitor.FileVisitDetailsImpl && fileDetails.filterChain.hasFilters())
            outputDir = new File(tempDir, fileDetails.name)
        }
        return outputDir
    }

    /**
     * Look at FileDetails to get a file. If it's filtered file, we need to write it out to the filesystem ourselves.
     * Issue #30, FileVisitDetailsImpl won't give us file, since it filters on the fly.
     */
    File extractFile(FileCopyDetailsInternal fileDetails) {
        File outputFile
        try {
            outputFile = fileDetails.getFile()
        } catch (UnsupportedOperationException uoe) {
            // Can't access MappingCopySpecVisitor.FileVisitDetailsImpl since it's private, so we have to probe. We would test this:
            // if (fileDetails instanceof MappingCopySpecVisitor.FileVisitDetailsImpl && fileDetails.filterChain.hasFilters())
            outputFile = new File(tempDir, fileDetails.path)
            fileDetails.copyTo(outputFile)
            filteredFiles << outputFile
        }
        return outputFile
    }
}
