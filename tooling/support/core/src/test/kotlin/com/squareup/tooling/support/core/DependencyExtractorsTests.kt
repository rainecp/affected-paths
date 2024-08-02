/*
 * Copyright (c) 2023 Square, Inc.
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
 *
 */

package com.squareup.tooling.support.core

import com.squareup.tooling.support.core.extractors.extractDependencies
import com.squareup.tooling.support.core.extractors.extractProjectDependenciesFromArtifacts
import com.squareup.tooling.support.core.extractors.extractSquareDependency
import com.squareup.tooling.support.core.models.SquareDependency
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.Path
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyExtractorsTests {

  @Test
  fun `Test extractDependencies() extension`() {
    // Setup
    val project = ProjectBuilder.builder().build()
    project.configurations.create("testConfig")
    project.dependencies.add("testConfig", "com.squareup:foo")
    project.dependencies.add("testConfig", "com.squareup:bar")

    val result = project.configurations.getByName("testConfig").extractDependencies().toList()

    // Test
    assertEquals(2, result.size)
    assertTrue(result.map { it.name }.containsAll(listOf("foo", "bar")))
  }

  @Test
  fun `Test extractSquareDependency() extension with AbstractExternalModuleDependency`() {
    // Setup
    val project = ProjectBuilder.builder().build()
    project.configurations.create("testConfig")
    project.dependencies.add("testConfig", "com.squareup:foo")

    // Test
    val configuration = project.configurations.getByName("testConfig")

    val result = configuration.dependencies.map { it.extractSquareDependency(project) }.first()

    assertTrue("AbstractExternalModuleDependency should not set tags") {
      return@assertTrue result.tags.isEmpty()
    }
    assertTrue("AbstractExternalModuleDependency target incorrect") {
      return@assertTrue result.target ==
        "@maven://com.squareup:foo"
    }
  }

  @Test
  fun `Test extractSquareDependency() with AbstractExternalModuleDependency undefined group`() {
    // Setup
    val project = ProjectBuilder.builder().build()
    project.configurations.create("testConfig")
    project.dependencies.add("testConfig", ":foo")

    // Test
    val configuration = project.configurations.getByName("testConfig")

    val result = configuration.dependencies.map { it.extractSquareDependency(project) }.first()

    assertTrue("AbstractExternalModuleDependency should not set tags") {
      return@assertTrue result.tags.isEmpty()
    }
    println(result)
    assertTrue("AbstractExternalModuleDependency target incorrect") {
      return@assertTrue result.target == "@maven://undefined:foo"
    }
  }

  @Test
  fun `Test extractSquareDependency() extension with AbstractModuleDependency`() {
    // Setup
    val project = ProjectBuilder.builder().build()
    val projectDependency = ProjectBuilder.builder().withName("squareTest").withParent(project).build()
    project.configurations.create("testConfig")
    project.dependencies.add("testConfig", projectDependency)

    // Test
    val configuration = project.configurations.getByName("testConfig")

    val result = configuration.dependencies.map { it.extractSquareDependency(project) }.first()

    assertEquals(
      expected = 1,
      actual = result.tags.size,
      message = "Transitive tag not applied"
    )
    println(result)
    assertTrue("AbstractModuleDependency target incorrect") {
      return@assertTrue result.target == "/squareTest"
    }
  }

  @Test
  fun `test extractProjectDependenciesFromArtifacts with empty artifacts`() {
    // Setup
    val project = ProjectBuilder.builder().build()
    project.repositories.mavenCentral()
    project.configurations.create("testConfig")
    project.dependencies.add("testConfig", "junit:junit:4.13.2")
    val projectDependency = ProjectBuilder.builder().withName("squareTest").withParent(project).build()
    projectDependency.configurations.create("default") // required default configuration
    project.dependencies.add("testConfig", projectDependency)

    // Test
    val configuration = project.configurations.getByName("testConfig")

    val result = configuration.extractProjectDependenciesFromArtifacts(project)

    assertTrue(result.none(), "The result should be an empty sequence")
  }

  @Test
  fun `test extractProjectDependenciesFromArtifacts with non-empty artifacts`() {
    // Setup
    val project = ProjectBuilder.builder().build()
    project.repositories.mavenCentral()
    project.configurations.create("testConfig")

    val configuration = mock<Configuration>()
    val artifact = mock<ResolvedArtifact>()

    val artifactId = mock<ComponentArtifactIdentifier>()
    whenever(artifact.id).thenReturn(artifactId)

    val id = mock<DefaultProjectComponentIdentifier>()
    whenever(artifactId.componentIdentifier).thenReturn(id)
    whenever(id.identityPath).thenReturn(Path.path(":includeBuild:project:path"))

    val resolvedConfiguration = mock<ResolvedConfiguration>()
    whenever(resolvedConfiguration.resolvedArtifacts).thenReturn(setOf(artifact))

    whenever(configuration.resolvedConfiguration).thenReturn(resolvedConfiguration)
    whenever(configuration.isCanBeResolved).thenReturn(true)

    // Test
    val result = configuration.extractProjectDependenciesFromArtifacts(project)

    assertEquals(1, result.count())
    assertTrue(result.contains(SquareDependency(target = "/includeBuild/project/path")))
  }
}
