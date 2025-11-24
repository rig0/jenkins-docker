/**
 * Docker Management Library for Jenkins Pipelines
 *
 * Provides comprehensive Docker container lifecycle management including
 * building, deploying, verifying, pushing to registries, and cleanup.
 *
 * Features:
 * - Docker image building from Dockerfiles
 * - Container deployment with automatic cleanup of old containers
 * - Health check verification with version validation
 * - Docker registry push with authentication
 * - Automatic cleanup of old images and containers
 *
 * Usage:
 *   @Library('jenkins-docker') _
 *
 *   // Build Docker image
 *   dockerLib.buildImage('myapp', 'latest')
 *
 *   // Deploy container
 *   dockerLib.deployContainer('myapp', 'myapp-container', '8080')
 *
 *   // Verify container is running with correct version
 *   dockerLib.verifyContainer('myapp-container', '1.2.3', '8080', '/api/version')
 *
 *   // Push to registry
 *   dockerLib.pushToRegistry('myapp', 'registry.example.com', '1.2.3', 'latest', 'DOCKER_CREDS')
 *
 *   // Clean up old images and containers
 *   dockerLib.cleanup('myapp', 'myapp-container', 'registry.example.com')
 */

/**
 * Build Docker image from Dockerfile
 *
 * Builds a Docker image from the Dockerfile in the current directory.
 * The build context is the current working directory.
 *
 * @param imageName Name of the Docker image to build
 * @param tag Tag to apply to the image (default: 'source')
 *
 * @example
 * dockerLib.buildImage('myapp', 'latest')
 *
 * @example
 * dockerLib.buildImage('myapp') // Uses default 'source' tag
 */
def buildImage(String imageName, String tag = 'source') {
  echo "🐳 Building Docker image: ${imageName}:${tag}"
  sh """
    docker build -t ${imageName}:${tag} .
  """
  echo "✅ Docker image built successfully"
}

/**
 * Deploy Docker container
 *
 * Stops and removes any existing container with the same name, then
 * starts a new container with the specified configuration.
 *
 * Default container settings:
 * - Runs as user 1000:1000
 * - Restart policy: always
 * - Detached mode
 *
 * @param imageName Name of the Docker image to use
 * @param containerName Name to give the container
 * @param port Port to expose (format: 'hostPort:containerPort' or 'port')
 * @param tag Image tag to use (default: 'source')
 *
 * @example
 * dockerLib.deployContainer('myapp', 'myapp-prod', '8080')
 *
 * @example
 * dockerLib.deployContainer('myapp', 'myapp-dev', '8080:3000', 'latest')
 */
def deployContainer(String imageName, String containerName, String port, String tag = 'source') {
  echo "🚀 Deploying container: ${containerName}"

  // Stop and remove existing container if it exists
  sh """
    docker stop ${containerName} 2>/dev/null || true
    docker rm ${containerName} 2>/dev/null || true
  """

  echo "Starting new container on port ${port}"
  sh """
    docker run -d \
      --name ${containerName} \
      --user 1000:1000 \
      -p ${port}:${port} \
      --restart always \
      ${imageName}:${tag}
  """

  echo "✅ Container deployed successfully"
}

/**
 * Verify container is running with correct version
 *
 * Polls a health check endpoint to verify the container has started
 * successfully and is running the expected version. Will retry up to
 * maxAttempts times with delaySeconds between attempts.
 *
 * The health endpoint must return JSON with a 'version' field that
 * matches the expectedVersion parameter.
 *
 * @param containerName Name of the container to verify
 * @param expectedVersion Expected version string to validate
 * @param port Port the container is listening on
 * @param healthEndpoint HTTP endpoint to check (e.g., '/api/version')
 * @param maxAttempts Maximum number of verification attempts (default: 10)
 * @param delaySeconds Seconds to wait between attempts (default: 5)
 *
 * @throws Error if container fails to start or version mismatch
 *
 * @example
 * dockerLib.verifyContainer('myapp', '1.2.3', '8080', '/health')
 *
 * @example
 * // Custom retry settings
 * dockerLib.verifyContainer('myapp', '1.2.3', '8080', '/api/version', 20, 3)
 */
def verifyContainer(String containerName, String expectedVersion, String port, String healthEndpoint, int maxAttempts = 10, int delaySeconds = 5) {
  echo "🔍 Verifying container: ${containerName}"
  echo "Expected version: ${expectedVersion}"

  def containerReady = false

  for (int i = 1; i <= maxAttempts; i++) {
    try {
      // Check if container is still running
      def containerRunning = sh(
        script: "docker ps -q -f name=${containerName}",
        returnStdout: true
      ).trim()

      if (!containerRunning) {
        error("Container ${containerName} is not running")
      }

      // Try to hit the health check endpoint
      def response = sh(
        script: "curl -s http://localhost:${port}${healthEndpoint}",
        returnStdout: true
      ).trim()

      // Parse JSON response to get version
      def version = sh(
        script: "echo '${response}' | jq -r '.version'",
        returnStdout: true
      ).trim()

      if (version == expectedVersion) {
        echo "✅ Container is running version ${version}"
        containerReady = true
        break
      } else {
        echo "⚠️ Container running wrong version: ${version} (expected ${expectedVersion})"
        error("Version mismatch")
      }
    } catch (Exception e) {
      echo "Attempt ${i}/${maxAttempts}: Container not ready yet..."
      if (i < maxAttempts) {
        sleep(delaySeconds)
      }
    }
  }

  if (!containerReady) {
    error("Container did not start successfully within ${maxAttempts * delaySeconds} seconds")
  }

  echo "✅ Container verification successful"
}

/**
 * Push Docker image to registry
 *
 * Tags and pushes a Docker image to a Docker registry. Creates two tags:
 * - {registry}/{imageName}:latest
 * - {registry}/{imageName}:{version}
 *
 * Requires Docker registry credentials to be configured in Jenkins.
 *
 * Security Notes:
 * - Logs in to registry using Jenkins credentials
 * - Automatically logs out after push
 * - Credentials are masked in Jenkins logs
 *
 * @param imageName Name of the local Docker image
 * @param registry Docker registry URL (e.g., 'dock.rigslab.com')
 * @param version Version tag to apply
 * @param sourceTag Local image tag to push (default: 'source')
 * @param credsId Jenkins credential ID for registry authentication
 *
 * @example
 * dockerLib.pushToRegistry('myapp', 'registry.example.com', '1.2.3', 'latest', 'DOCKER_CREDS')
 *
 * @example
 * // Push from source tag
 * dockerLib.pushToRegistry('myapp', 'dock.rigslab.com', '1.2.3', 'source', 'MY_REGISTRY_CREDS')
 */
def pushToRegistry(String imageName, String registry, String version, String sourceTag = 'source', String credsId) {
  echo "📤 Pushing to registry: ${registry}"

  withCredentials([usernamePassword(
    credentialsId: credsId,
    usernameVariable: 'DOCKER_USER',
    passwordVariable: 'DOCKER_PASS'
  )]) {
    sh """
      echo \$DOCKER_PASS | docker login ${registry} -u \$DOCKER_USER --password-stdin

      docker tag ${imageName}:${sourceTag} ${registry}/${imageName}:latest
      docker tag ${imageName}:${sourceTag} ${registry}/${imageName}:${version}

      docker push ${registry}/${imageName}:latest
      docker push ${registry}/${imageName}:${version}

      docker logout ${registry}
    """
  }

  echo "✅ Images pushed successfully"
  echo "   - ${registry}/${imageName}:latest"
  echo "   - ${registry}/${imageName}:${version}"
}

/**
 * Clean up Docker artifacts for a project
 *
 * Removes old Docker containers and images to free up disk space.
 * Only removes artifacts related to the specified project, leaving
 * all other Docker resources untouched.
 *
 * Cleanup operations:
 * 1. Remove backup containers (e.g., myapp-backup)
 * 2. Remove dangling/untagged images
 * 3. Remove old local images (keeps only the source tag)
 * 4. Remove old registry images (keeps only latest and current version)
 *
 * Safety:
 * - Only removes images/containers matching the specified names
 * - Keeps the current source image
 * - Keeps the latest registry tag
 * - Keeps the current version registry tag
 * - All operations use || true to prevent build failure
 *
 * @param imageName Name of the Docker image to clean
 * @param containerName Name of the container to clean
 * @param registry Optional registry URL to clean registry images
 *
 * @example
 * dockerLib.cleanup('myapp', 'myapp-container')
 *
 * @example
 * // Also clean registry images
 * dockerLib.cleanup('myapp', 'myapp-container', 'registry.example.com')
 */
def cleanup(String imageName, String containerName, String registry = null) {
  echo "🧹 Cleaning up Docker artifacts for ${imageName}"

  // Remove stopped containers
  sh """
    docker ps -a -q -f name=${containerName}-backup | xargs -r docker rm -f || true
  """

  // Remove dangling/untagged images
  sh """
    docker images -f "dangling=true" -q | xargs -r docker rmi -f || true
  """

  // Remove old local images (keep only source tag)
  sh """
    docker images ${imageName} --format "{{.Tag}}" | grep -v "^source\$" | xargs -r -I {} docker rmi ${imageName}:{} -f || true
  """

  // If registry is provided, clean up old registry images (keep latest and current version)
  if (registry) {
    sh """
      docker images ${registry}/${imageName} --format "{{.Tag}}" | grep -v "^latest\$" | grep -v "^${env.CLEAN_VERSION}\$" | xargs -r -I {} docker rmi ${registry}/${imageName}:{} -f || true
    """
  }

  echo "✅ Cleanup completed"
}

// Make this library callable
return this
