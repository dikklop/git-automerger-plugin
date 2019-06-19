package com.vinted.automerger

import com.vinted.automerger.config.Resolution
import com.vinted.automerger.config.MergeRule
import com.vinted.automerger.testutils.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File

class ConflictRepoTest {
    @RegisterExtension
    @JvmField
    val repo: RepoExtension = DefaultRepoExtension()

    private lateinit var defaultBuilder: AutoMergerBuilder
    private lateinit var reverseBuilder: AutoMergerBuilder

    private lateinit var changelogFile: File
    private lateinit var versionFile: File

    @BeforeEach
    fun setUp() {
        changelogFile = repo.path.resolve("changelog.txt")
        versionFile = repo.path.resolve("version.txt")

        defaultBuilder = AutoMergerBuilder()
            .pathToRepo(repo.path)
            .addMergeConfig(MergeRule(versionFile.name, Resolution.KEEP_NEWER))
            .addMergeConfig(MergeRule(changelogFile.name, Resolution.MERGE_NEWER_TOP))

        reverseBuilder = AutoMergerBuilder()
            .pathToRepo(repo.path)
            .addMergeConfig(MergeRule(versionFile.name, Resolution.KEEP_OLDER))
            .addMergeConfig(MergeRule(changelogFile.name, Resolution.MERGE_OLDER_TOP))
    }

    @Nested
    inner class SimpleCase {
        @BeforeEach
        fun setUp() {
            with(repo.git) {
                checkoutBranch("release/8.10")
                versionFile.writeText("8.10")
                changelogFile.writeText("foo")
                commitAll("Update 1")

                checkoutMaster()
                versionFile.writeText("9.0")
                changelogFile.writeText("bar")
                add().addFilepattern(".").call()
                commitAll("Update 2")
            }
        }

        @Test
        fun default() {
            defaultBuilder.build().automerge()
            repo.git.checkoutMaster()

            assertEquals("9.0", versionFile.readText().trimEnd())
            assertEquals("bar\nfoo", changelogFile.readText().trimEnd())
        }

        @Test
        fun reverse() {
            reverseBuilder.build().automerge()
            repo.git.checkoutMaster()

            assertEquals("8.10", versionFile.readText().trimEnd())
            assertEquals("foo\nbar", changelogFile.readText().trimEnd())
        }
    }

    @Test
    fun notConfiguredConflict() {
        val file = repo.path.resolve("foo.txt")
        with(repo.git) {
            checkoutBranch("release/8.10")
            file.writeText("foo")
            commitAll("Update 1")

            checkoutMaster()
            file.writeText("bar")
            commitAll("Update 2")
        }

        assertThrows<Exception> {
            defaultBuilder.build().automerge()
        }

        assertFalse(file.readText().contains("====="), "Automerger should not leave any conflict markers")
    }
}
