# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Jenkins plugin (`hpi` packaging, groupId `de.taimos`, artifactId `pipeline-aws`) that adds ~45 pipeline steps for
interacting with AWS: `withAWS`, S3, CloudFormation (incl. stack sets), ECR, Lambda, Elastic Beanstalk, ELB, CodeDeploy,
API Gateway, SNS, IAM/Organizations. It targets the Jenkins baseline in `pom.xml` (`jenkins.baseline` property) and uses
the **AWS SDK for Java v1** (`com.amazonaws.*`, repackaged as `org.jenkins-ci.plugins.aws-java-sdk-*` deps).

## Build & test

Maven only; there is no wrapper (`.mvn/maven.config` enables the incrementals profiles). Jenkins plugin parent POM.

```bash
mvn clean verify                  # full build: checkstyle (validate phase), tests, hpi package
mvn test                          # unit + JenkinsRule tests
mvn test -Dtest=S3UploadStepTest  # single test class
mvn test -Dtest=CFNDescribeStackTests#describe   # single test method
mvn hpi:run                       # run a local Jenkins with the plugin loaded (http://localhost:8080/jenkins)
```

Checkstyle (`checkstyle.xml`) runs in the `validate` phase and **fails the build**. It enforces three things:

- `RequireThis` — every field/method access on `this` must be written `this.foo` (a very common cause of build failures).
- No star imports.
- **Indentation must be tabs**, not spaces (regex check on leading whitespace).

CI (`Jenkinsfile`) builds on linux/JDK 21 and windows/JDK 17 via the jenkins-infra `buildPlugin` library.

## Architecture

### Step anatomy

Every pipeline step lives under `src/main/java/de/taimos/pipeline/aws/` (subpackages: `cloudformation`,
`cloudformation/stacksets`, `cloudformation/parser`, `eb`, `ecr`, `elb`, `code/deploy`, `utils`) and follows one shape —
copy an existing step (e.g. `SNSPublishStep.java`) when adding a new one:

1. `public class FooStep extends Step` with a `@DataBoundConstructor` for required params and `@DataBoundSetter`
   setters for optional ones (plus getters — the Jenkins UI and tests rely on them).
2. `start(StepContext)` returns a nested `public static class Execution extends SynchronousNonBlockingStepExecution<T>`
   whose `run()` does the AWS work. Keep `private final transient FooStep step;` and a `serialVersionUID`.
3. `@Extension public static class DescriptorImpl extends StepDescriptor` supplying `getFunctionName()` (the Groovy step
   name), `getDisplayName()`, and `getRequiredContext()` — usually `StepUtils.requiresDefault()`
   (`EnvVars` + `TaskListener`), or `StepUtils.requires(TaskListener.class, EnvVars.class, FilePath.class)` for steps
   that touch the workspace.

### Credentials/region flow

`withAWS` does **not** create clients. Its `Execution` builds an `EnvVars` overlay (`AWS_ACCESS_KEY_ID`,
`AWS_SESSION_TOKEN`, `AWS_REGION`, `AWS_ENDPOINT_URL`, `AWS_PROFILE`, `AWS_PIPELINE_STEPS_FROM_NODE`, …) and pushes it
into the body via an `EnvironmentExpander`. Every step then calls
`AWSClientFactory.create(SomeClientBuilder.standard(), this.getContext())`, which reads those env vars to set region,
credentials, retries (`AWS_SDK_RETRIES`, default 10), socket timeout (`AWS_SDK_SOCKET_TIMEOUT`) and proxy config.
Adding a new auth/config knob means touching `WithAWSStep` (to export the var) and `AWSClientFactory` (to consume it).

### Primary vs. agent execution

Most steps run on the Jenkins controller. Only workspace-touching work is shipped to the agent, as
`MasterToSlaveFileCallable` subclasses invoked through `FilePath.act(...)` — see `RemoteUploader`/`RemoteListUploader`
in `S3UploadStep` and `RemoteDownloader` in `S3DownloadStep`. Those callables re-create their own S3 client on the agent
from serialized options (`S3ClientOptions` + `EnvVars`), so anything they need must be `Serializable`.
`AWSCredentialsProviderCallable` implements the "retrieve credentials from node" mode.

### Test seams

There is no live AWS in tests. Two static injection points exist purely for testing and are the intended way to test a
step end-to-end:

- `AWSClientFactory.setFactoryDelegate(builder -> mockClient)` — every `AWSClientFactory.create` returns the mock.
- `AWSUtilFactory.setStackSupplier / setStackSetSupplier / setV2TransferManagerSupplier` — swap out `CloudFormationStack`,
  `CloudFormationStackSet`, `TransferManager`.

**Always reset these to `null` in `@After`**; they are static and leak across test classes.

Test styles in `src/test/java`: plain JUnit 4 + Mockito for getter/validation logic (`*StepTest`), and `@Rule JenkinsRule`
+ `WorkflowJob` with a `CpsFlowDefinition` script asserted via `assertBuildStatusSuccess` / `assertLogContains` for real
step execution (`*Tests`, `*IntegrationTest`). AssertJ and Hamcrest are both available.

### UI metadata

`src/main/resources/de/taimos/pipeline/aws/<StepClassName>/` holds `config.jelly` and `help-<param>.html` files used by
the Snippet Generator. When adding or renaming a step parameter, add the matching `help-<param>.html`.

## Conventions for changes

- Document every new/changed step in `README.md` (it is the plugin's user documentation — one `##` section per step,
  linked from the feature list at the top) and add a bullet under `## current master` in the `# Changelog` section.
- Keep the Apache-2.0 license header block at the top of new Java files (copy from a neighbouring file).
- Lombok is available (`provided` scope) but used sparingly; match the surrounding file.
