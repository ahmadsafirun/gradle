/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableSortedMap
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionEngine
import org.gradle.internal.execution.ImmutableUnitOfWork
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.ExecutionOutputState
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadata
import org.gradle.internal.execution.history.ImmutableWorkspaceMetadataStore
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider
import org.gradle.internal.file.Deleter
import org.gradle.internal.file.FileType
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.TestSnapshotFixture
import org.gradle.internal.vfs.FileSystemAccess
import spock.lang.Issue

import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE
import static org.gradle.internal.execution.steps.AssignImmutableWorkspaceStep.LockingStrategy
import static org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.AtomicMoveImmutableWorkspace.TemporaryWorkspaceAction
import static org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider.AtomicMoveImmutableWorkspace

class AssignImmutableWorkspaceStepWithAtomicMoveTest extends StepSpec<IdentityContext> implements TestSnapshotFixture {
    def immutableWorkspace = file("immutable-workspace")
    def temporaryWorkspace = file("temporary-workspace")
    def secondTemporaryWorkspace = file("second-temporary-workspace")
    def workspace = Stub(AtomicMoveImmutableWorkspace) {
        immutableLocation >> immutableWorkspace
        withTemporaryWorkspace(_ as TemporaryWorkspaceAction)
            >>
            { TemporaryWorkspaceAction action ->
                action.executeInTemporaryWorkspace(temporaryWorkspace)
            }
            >>
            { TemporaryWorkspaceAction action ->
                action.executeInTemporaryWorkspace(secondTemporaryWorkspace)
            }
    }

    def deleter = Mock(Deleter)
    def fileSystemAccess = Mock(FileSystemAccess)
    def immutableWorkspaceMetadataStore = Mock(ImmutableWorkspaceMetadataStore)
    def outputSnapshotter = Mock(OutputSnapshotter)
    def workspaceProvider = Stub(ImmutableWorkspaceProvider) {
        getAtomicMoveWorkspace(workId) >> workspace
    }

    def step = new AssignImmutableWorkspaceStep(deleter, fileSystemAccess, immutableWorkspaceMetadataStore, outputSnapshotter, delegate, LockingStrategy.ATOMIC_MOVE)
    def work = Stub(ImmutableUnitOfWork)

    def setup() {
        work.workspaceProvider >> workspaceProvider
    }

    def "returns immutable workspace when already exists"() {
        def outputFile = immutableWorkspace.file("output.txt")
        def outputFileSnapshot = regularFile(outputFile.absolutePath)

        def delegateOriginMetadata = Mock(OriginMetadata)
        def existingWorkspaceSnapshot = Stub(DirectorySnapshot) {
            type >> FileType.Directory
        }

        def existingOutputs = ImmutableSortedMap.<String, FileSystemLocationSnapshot> of(
            "output", outputFileSnapshot
        )
        def originalWorkspaceMetadata = Stub(ImmutableWorkspaceMetadata) {
            getOriginMetadata() >> delegateOriginMetadata
            getOutputPropertyHashes() >> ImmutableListMultimap.of("output", outputFileSnapshot.hash)
        }

        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == UP_TO_DATE
        result.afterExecutionOutputState.get().successful
        result.afterExecutionOutputState.get().originMetadata == delegateOriginMetadata
        result.afterExecutionOutputState.get().outputFilesProducedByWork == existingOutputs
        result.reusedOutputOriginMetadata.get() == delegateOriginMetadata

        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> existingWorkspaceSnapshot

        then:
        1 * immutableWorkspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace) >> Optional.of(originalWorkspaceMetadata)

        then:
        1 * outputSnapshotter.snapshotOutputs(work, immutableWorkspace) >> existingOutputs
        0 * _
    }

    def "runs in temporary workspace when immutable workspace doesn't exist"() {
        def delegateExecution = Mock(ExecutionEngine.Execution)
        def delegateDuration = Duration.ofSeconds(1)
        def delegateOriginMetadata = Stub(OriginMetadata)
        def delegateOutputFiles = ImmutableSortedMap.of()
        def delegateOutputState = Stub(ExecutionOutputState) {
            getOriginMetadata() >> delegateOriginMetadata
            getOutputFilesProducedByWork() >> delegateOutputFiles
        }
        def delegateResult = Stub(CachingResult) {
            getExecution() >> Try.successful(delegateExecution)
            getDuration() >> delegateDuration
            getAfterExecutionOutputState() >> Optional.of(delegateOutputState)
        }
        def resolvedDelegateResult = Stub(Object)

        when:
        def result = step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> Stub(MissingFileSnapshot) {
            type >> FileType.Missing
        }

        then:
        1 * fileSystemAccess.invalidate([temporaryWorkspace.absolutePath])

        then:
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext delegateContext ->
            assert delegateContext.workspace == temporaryWorkspace
            temporaryWorkspace.file("output.txt").text = "output"
            return delegateResult
        }

        then:
        1 * immutableWorkspaceMetadataStore.storeWorkspaceMetadata(temporaryWorkspace, _) >> { File workspace, ImmutableWorkspaceMetadata metadata ->
            metadata.originMetadata == delegateOriginMetadata
        }

        then:
        1 * fileSystemAccess.moveAtomically(temporaryWorkspace.absolutePath, immutableWorkspace.absolutePath) >> { String from, String to ->
            Files.move(Paths.get(from), Paths.get(to))
        }

        then:
        immutableWorkspace.file("output.txt").text == "output"
        0 * _

        when:
        def resolvedResult = result.getOutputAs(Object)

        then:
        resolvedResult.get() == resolvedDelegateResult

        1 * delegateExecution.getOutput(immutableWorkspace) >> resolvedDelegateResult
        0 * _
    }

    @Issue("https://github.com/gradle/gradle/issues/27844")
    def "falls back to duplicating temporary workspace when the original cannot be moved atomically"() {
        def delegateExecution = Mock(ExecutionEngine.Execution)
        def delegateDuration = Duration.ofSeconds(1)
        def delegateOriginMetadata = Stub(OriginMetadata)
        def delegateOutputFiles = ImmutableSortedMap.of()
        def delegateOutputState = Stub(ExecutionOutputState) {
            getOriginMetadata() >> delegateOriginMetadata
            getOutputFilesProducedByWork() >> delegateOutputFiles
        }
        def delegateResult = Stub(CachingResult) {
            getExecution() >> Try.successful(delegateExecution)
            getDuration() >> delegateDuration
            getAfterExecutionOutputState() >> Optional.of(delegateOutputState)
        }

        when:
        step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> Stub(MissingFileSnapshot) {
            type >> FileType.Missing
        }

        then:
        1 * fileSystemAccess.invalidate([temporaryWorkspace.absolutePath])

        then:
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext delegateContext ->
            assert delegateContext.workspace == temporaryWorkspace
            temporaryWorkspace.file("output.txt").text = "output"
            return delegateResult
        }

        then:
        1 * immutableWorkspaceMetadataStore.storeWorkspaceMetadata(temporaryWorkspace, _)
        1 * fileSystemAccess.moveAtomically(temporaryWorkspace.absolutePath, immutableWorkspace.absolutePath) >> { String from, String to ->
            throw new AccessDeniedException("Simulate Windows keeping file locks open")
        }

        then:
        1 * fileSystemAccess.moveAtomically(secondTemporaryWorkspace.absolutePath, immutableWorkspace.absolutePath) >> { String from, String to ->
            Files.move(Paths.get(from), Paths.get(to))
        }

        then:
        1 * deleter.deleteRecursively(temporaryWorkspace)

        then:
        immutableWorkspace.file("output.txt").text == "output"
        0 * _
    }

    def "keeps failed outputs in temporary workspace"() {
        def delegateFailure = Mock(Exception)
        def delegateDuration = Duration.ofSeconds(1)
        def delegateResult = Stub(CachingResult) {
            execution >> Try.failure(delegateFailure)
            duration >> delegateDuration
        }

        when:
        def result = step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> Stub(MissingFileSnapshot) {
            type >> FileType.Missing
        }

        then:
        1 * fileSystemAccess.invalidate([temporaryWorkspace.absolutePath])

        then:
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext delegateContext ->
            assert delegateContext.workspace == temporaryWorkspace
            temporaryWorkspace.file("output.txt").text = "output"
            return delegateResult
        }

        then:
        immutableWorkspace.assertDoesNotExist()
        temporaryWorkspace.file("output.txt").assertIsFile()
        temporaryWorkspace.file("origin.bin").assertDoesNotExist()
        0 * _

        when:
        def resolvedResult = result.getOutputAs(Object)

        then:
        resolvedResult.failure.get() == delegateFailure
        0 * _
    }

    def "fails when immutable workspace has been tampered with"() {
        def outputFile = immutableWorkspace.file("output.txt")
        outputFile.text = "output"

        def originalWorkspaceSnapshot = directory(immutableWorkspace.absolutePath, [
            regularFile(outputFile.absolutePath, 1234L)
        ])
        def originalWorkspaceMetadata = Stub(ImmutableWorkspaceMetadata) {
            getOriginMetadata() >> Stub(OriginMetadata)
            getOutputPropertyHashes() >> ImmutableListMultimap.of("output", originalWorkspaceSnapshot.hash)
        }
        def tamperedWorkspaceSnapshot = directory(immutableWorkspace.absolutePath, [
            regularFile(outputFile.absolutePath, 2345L)
        ])
        def tamperedOutputs = ImmutableSortedMap.<String, FileSystemLocationSnapshot> of(
            "output", tamperedWorkspaceSnapshot
        )

        when:
        step.execute(work, context)

        then:
        1 * fileSystemAccess.read(immutableWorkspace.absolutePath) >> tamperedWorkspaceSnapshot
        1 * outputSnapshotter.snapshotOutputs(work, immutableWorkspace) >> tamperedOutputs
        1 * immutableWorkspaceMetadataStore.loadWorkspaceMetadata(immutableWorkspace) >> Optional.of(originalWorkspaceMetadata)

        then:
        def ex = thrown IllegalStateException
        ex.message.trim() == """
            The contents of the immutable workspace '${immutableWorkspace.absolutePath}' have been modified. These workspace directories are not supposed to be modified once they are created. The modification might have been caused by an external process, or could be the result of disk corruption.
            output:
             - immutable-workspace (Directory, 81e73263e0f667c2cbec4e2af9fcc55f)
               - output.txt (RegularFile, 29090000000000000000000000000000)
            """.stripIndent().trim()
    }
}
