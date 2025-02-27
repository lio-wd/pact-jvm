package au.com.dius.pact.provider.maven

import au.com.dius.pact.core.pactbroker.PactBrokerClient
import au.com.dius.pact.core.pactbroker.PublishConfiguration
import au.com.dius.pact.core.pactbroker.RequestFailedException
import au.com.dius.pact.core.support.isNotEmpty
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import java.io.File

/**
 * Task to push pact files to a pact broker
 */
@Mojo(name = "publish")
open class PactPublishMojo : PactBaseMojo() {

    @Parameter(defaultValue = "false", property = "skipPactPublish")
    private var skipPactPublish: Boolean = false

    @Parameter(required = true, defaultValue = "\${project.version}", property = "pact.projectVersion")
    private lateinit var projectVersion: String

    @Parameter(defaultValue = "false", property = "pact.trimSnapshot")
    private var trimSnapshot: Boolean = false

    @Parameter(defaultValue = "\${project.build.directory}/pacts", property = "pact.pactDirectory")
    private lateinit var pactDirectory: String

    private var brokerClient: PactBrokerClient? = null

    @Parameter
    private var tags: MutableList<String> = mutableListOf()

    @Parameter
    private var excludes: MutableList<String> = mutableListOf()

  @Parameter
  var branchName: String? = null
  @Parameter
  var buildUrl: String? = null

    override fun execute() {
      if (skipPactPublish) {
        println("'skipPactPublish' is set to true, skipping uploading of pacts")
        return
      }

      if (pactBrokerUrl.isNullOrEmpty() && brokerClient == null) {
        throw MojoExecutionException("pactBrokerUrl is required")
      }

      val snapShotDefinitionString = "-SNAPSHOT"
      if (trimSnapshot && projectVersion.contains(snapShotDefinitionString)) {
        val snapshotRegex = Regex(".*($snapShotDefinitionString)")
        projectVersion = projectVersion.removeRange(snapshotRegex.find(projectVersion)!!.groups[1]!!.range)
      }

      if (brokerClient == null) {
        brokerClient = PactBrokerClient(pactBrokerUrl!!, brokerClientOptions(), brokerClientConfig())
      }

      val pactDirectory = File(pactDirectory)
      val tagsToPublish = calculateTags()
      val publishConfiguration = PublishConfiguration(projectVersion, tagsToPublish, branchName, buildUrl)

      if (!pactDirectory.exists()) {
        println("Pact directory $pactDirectory does not exist, skipping uploading of pacts")
      } else {
        val excludedList = this.excludes.map { Regex(it) }
        var anyFailed = false
        pactDirectory.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach { pactFile ->
          if (pactFileIsExcluded(excludedList, pactFile)) {
            println("Not publishing '${pactFile.name}' as it matches an item in the excluded list")
          } else {
            if (tagsToPublish.isNotEmpty()) {
              println("Publishing '${pactFile.name}' with tags '${tagsToPublish.joinToString(", ")}' ... ")
            } else {
              println("Publishing '${pactFile.name}' ... ")
            }
            when (val result = brokerClient!!.uploadPactFile(pactFile, publishConfiguration)) {
                is Ok -> println("OK")
                is Err -> {
                  val error = result.error
                  println("Failed - ${result.error.message}")
                  if (error is RequestFailedException && error.body.isNotEmpty()) {
                    println(error.body)
                  }
                  anyFailed = true
                }
              }
            }
          }

        if (anyFailed) {
          throw MojoExecutionException("One or more of the pact files were rejected by the pact broker")
        }
      }
    }

  private fun calculateTags(): List<String> {
    val property = System.getProperty("pact.consumer.tags")
    return if (property.isNotEmpty()) {
      property.split(',').map { it.trim() }
    } else {
      tags
    }
  }

  private fun pactFileIsExcluded(exclusions: List<Regex>, pactFile: File) =
    exclusions.any { it.matches(pactFile.nameWithoutExtension) }
}
