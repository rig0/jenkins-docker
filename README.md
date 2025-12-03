<div align="center">

# Jenkins Docker Library

![Jenkins](https://img.shields.io/badge/Jenkins-D24939?logo=jenkins&logoColor=white)
![CI/CD](https://img.shields.io/badge/CI%2FCD-239120?logo=gitlab&logoColor=white)
![Groovy](https://img.shields.io/badge/Groovy-5a92a7?logo=apachegroovy&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

![Latest Tag](https://img.shields.io/github/v/tag/rig0/jenkins-docker?labelColor=222&color=80ff63&label=latest)
![Maintained](https://img.shields.io/badge/maintained-yes-80ff63?labelColor=222)
![GitHub last commit](https://img.shields.io/github/last-commit/rig0/jenkins-docker?labelColor=222&color=80ff63)


**A comprehensive Jenkins shared library for Docker container lifecycle management. Provides reusable functions for building, deploying, verifying, pushing, and cleaning up Docker containers and images.**

</div>

## Features

- **Build Images**: Build Docker images from Dockerfiles
- **Deploy Containers**: Deploy containers with automatic cleanup of old instances
- **Health Verification**: Verify container health with version validation
- **Registry Push**: Push images to Docker registries with authentication
- **Automatic Cleanup**: Clean up old containers and images to free disk space

## Prerequisites

- Jenkins with Docker installed and configured
- Docker socket mounted in Jenkins container (`/var/run/docker.sock`)
- Required tools in Jenkins:
  - `docker` - Docker CLI
  - `curl` - For health checks
  - `jq` - For JSON parsing
- Docker registry credentials configured in Jenkins (if using registry push)

## Installation

### Option 1: Global Shared Library (Recommended)

1. In Jenkins, go to **Manage Jenkins** → **Configure System**
2. Scroll to **Global Pipeline Libraries**
3. Click **Add** and configure:
   - **Name**: `jenkins-docker`
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: `https://github.com/rig0/jenkins-docker.git`

### Option 2: Pipeline-Specific Library

Add to the top of your Jenkinsfile:

```groovy
@Library('jenkins-docker@main') _
```

## Usage

### Basic Example

```groovy
@Library(['jenkins-docker']) _

pipeline {
  agent any

  environment {
    IMAGE_NAME = 'myapp'
    CONTAINER_NAME = 'myapp'
    VERSION = '1.2.3'
    PORT = '8080'
  }

  stages {
    stage('Build') {
      steps {
        script {
          dockerLib.buildImage(env.IMAGE_NAME, 'source')
        }
      }
    }

    stage('Deploy') {
      steps {
        script {
          dockerLib.deployContainer(
            env.IMAGE_NAME,
            env.CONTAINER_NAME,
            env.PORT,
            'source'
          )
        }
      }
    }

    stage('Verify') {
      steps {
        script {
          dockerLib.verifyContainer(
            env.CONTAINER_NAME,
            env.VERSION,
            'localhost',
            env.PORT,
            '/api/version'
          )
        }
      }
    }

    stage('Push') {
      steps {
        script {
          dockerLib.pushToRegistry(
            env.IMAGE_NAME,
            'registry.example.com',
            env.VERSION,
            'source',
            'DOCKER_REGISTRY_CREDS'
          )
        }
      }
    }

    stage('Cleanup') {
      steps {
        script {
          dockerLib.cleanup(
            env.IMAGE_NAME,
            env.CONTAINER_NAME,
            'registry.example.com'
          )
        }
      }
    }
  }
}
```

## API Reference

### `buildImage(imageName, tag)`

Builds a Docker image from the Dockerfile in the current directory.

**Parameters:**
- `imageName` (String, required): Name of the Docker image to build
- `tag` (String, optional): Tag to apply to the image (default: `'source'`)

**Example:**
```groovy
dockerLib.buildImage('myapp', 'latest')
dockerLib.buildImage('myapp') // Uses default 'source' tag
```

---

### `deployContainer(imageName, containerName, port, tag)`

Deploys a Docker container, automatically stopping and removing any existing container with the same name.

**Parameters:**
- `imageName` (String, required): Name of the Docker image to use
- `containerName` (String, required): Name to give the container
- `port` (String, required): Port to expose (format: 'hostPort:containerPort' or 'port')
- `tag` (String, optional): Image tag to use (default: `'source'`)

**Container Settings:**
- Runs as user 1000:1000
- Restart policy: always
- Detached mode

**Example:**
```groovy
dockerLib.deployContainer('myapp', 'myapp-prod', '8080')
dockerLib.deployContainer('myapp', 'myapp-dev', '8080:3000', 'latest')
```

---

### `verifyContainer(containerName, expectedVersion, host, port, healthEndpoint, maxAttempts, delaySeconds)`

Verifies that a container has started successfully and is running the expected version.

**Parameters:**
- `containerName` (String, required): Name of the container to verify
- `expectedVersion` (String, required): Expected version string to validate
- `host` (String, required): Host/IP address where the container is accessible (e.g., 'localhost', '10.1.4.2')
- `port` (String, required): Port the container is listening on
- `healthEndpoint` (String, required): HTTP endpoint to check (e.g., '/api/version')
- `maxAttempts` (Integer, optional): Maximum number of verification attempts (default: 10)
- `delaySeconds` (Integer, optional): Seconds to wait between attempts (default: 5)

**Requirements:**
- Health endpoint must return JSON with a 'version' field
- Version field must match expectedVersion exactly

**Example:**
```groovy
dockerLib.verifyContainer('myapp', '1.2.3', 'localhost', '8080', '/health')

// Custom retry settings
dockerLib.verifyContainer('myapp', '1.2.3', '127.0.0.1', '8080', '/api/version', 20, 3)

// Verify container on different host (e.g., Jenkins in container accessing app container by IP)
dockerLib.verifyContainer('myapp', '1.2.3', '10.1.4.2', '5182', '/api/version', 20, 3)
```

---

### `pushToRegistry(imageName, registry, version, sourceTag, credsId)`

Tags and pushes a Docker image to a Docker registry. Creates two tags: `latest` and the specified version.

**Parameters:**
- `imageName` (String, required): Name of the local Docker image
- `registry` (String, required): Docker registry URL (e.g., 'dock.rigslab.com')
- `version` (String, required): Version tag to apply
- `sourceTag` (String, optional): Local image tag to push (default: `'source'`)
- `credsId` (String, required): Jenkins credential ID for registry authentication

**Security:**
- Logs in to registry using Jenkins credentials
- Automatically logs out after push
- Credentials are masked in Jenkins logs

**Example:**
```groovy
dockerLib.pushToRegistry('myapp', 'registry.example.com', '1.2.3', 'source', 'DOCKER_CREDS')
```

---

### `cleanup(imageName, containerName, registry)`

Removes old Docker containers and images to free up disk space. Only removes artifacts related to the specified project.

**Parameters:**
- `imageName` (String, required): Name of the Docker image to clean
- `containerName` (String, required): Name of the container to clean
- `registry` (String, optional): Registry URL to clean registry images

**Cleanup Operations:**
1. Remove backup containers (e.g., myapp-backup)
2. Remove dangling/untagged images
3. Remove old local images (keeps only the source tag)
4. Remove old registry images (keeps only latest and current version)

**Safety:**
- Only removes images/containers matching the specified names
- Keeps the current source image
- Keeps the latest registry tag
- Keeps the current version registry tag
- All operations use `|| true` to prevent build failure

**Example:**
```groovy
dockerLib.cleanup('myapp', 'myapp-container')

// Also clean registry images
dockerLib.cleanup('myapp', 'myapp-container', 'registry.example.com')
```

## Advanced Usage

### Conditional Docker Operations

Only build and deploy when version is bumped:

```groovy
@Library(['jenkins-version', 'jenkins-docker']) _

pipeline {
  agent any

  stages {
    stage('Determine Version') {
      steps {
        script {
          def versionInfo = versionLib.determineVersion()
          env.VERSION = versionInfo.cleanVersion
          env.BUMP_TYPE = versionInfo.bumpType
        }
      }
    }

    stage('Build Docker Image') {
      when {
        expression { env.BUMP_TYPE && env.BUMP_TYPE != '' }
      }
      steps {
        script {
          dockerLib.buildImage('myapp', 'source')
        }
      }
    }

    stage('Deploy Container') {
      when {
        expression { env.BUMP_TYPE && env.BUMP_TYPE != '' }
      }
      steps {
        script {
          dockerLib.deployContainer('myapp', 'myapp', '8080', 'source')
        }
      }
    }
  }
}
```

### Custom Health Check with Retries

```groovy
stage('Verify Container') {
  steps {
    script {
      // Wait up to 2 minutes (24 attempts × 5 seconds)
      dockerLib.verifyContainer(
        'myapp',
        env.VERSION,
        'localhost',
        '8080',
        '/api/health',
        24,
        5
      )
    }
  }
}
```

### Multiple Registry Push

```groovy
stage('Push to Registries') {
  steps {
    script {
      // Push to production registry
      dockerLib.pushToRegistry(
        'myapp',
        'registry.example.com',
        env.VERSION,
        'source',
        'PROD_DOCKER_CREDS'
      )

      // Push to backup registry
      dockerLib.pushToRegistry(
        'myapp',
        'backup.registry.com',
        env.VERSION,
        'source',
        'BACKUP_DOCKER_CREDS'
      )
    }
  }
}
```

## Troubleshooting

### Docker Socket Permission Denied

**Problem:** `permission denied while trying to connect to the Docker daemon socket`

**Solution:** Ensure Docker socket is mounted in Jenkins container:
```bash
docker run -v /var/run/docker.sock:/var/run/docker.sock jenkins/jenkins
```

### Container Verification Timeout

**Problem:** `Container did not start successfully within X seconds`

**Solutions:**
1. Increase `maxAttempts` and `delaySeconds` parameters
2. Check container logs: `docker logs <container-name>`
3. Verify health endpoint is accessible
4. Ensure application starts quickly enough
5. If Jenkins runs in a container, verify the `host` parameter:
   - Use `localhost` if containers share the host network
   - Use container IP (e.g., `10.1.4.2`) if on a Docker bridge network
   - Use container name if on a custom Docker network with DNS

### Registry Push Authentication Failed

**Problem:** `unauthorized: authentication required`

**Solution:**
1. Verify credentials exist in Jenkins: **Manage Jenkins** → **Credentials**
2. Ensure credential ID matches the `credsId` parameter
3. Test registry login manually: `docker login <registry>`

### Cleanup Removes Too Much

**Problem:** Important images are being removed

**Solution:**
- The cleanup function only removes images matching the exact `imageName` and `containerName` provided
- Current source image and registry tags (latest + current version) are always kept
- Review the image naming to ensure no conflicts

## Best Practices

1. **Always verify containers** after deployment to catch startup failures early
2. **Use cleanup stage** in post-build actions to prevent disk space issues
3. **Store registry credentials** in Jenkins credential store, never hardcode
4. **Use semantic versioning** for image tags to track releases properly
5. **Tag with both version and 'latest'** for flexibility in deployments
6. **Run cleanup conditionally** only when docker operations succeed
7. **Use health checks** that validate actual application readiness, not just HTTP 200

## Integration with Other Libraries

This library works well with:

- **jenkins-version**: Automatic semantic versioning and Git tagging
- **jenkins-pushover**: Send notifications on deployment success/failure
- **jenkins-deployment**: Coordinate multi-service deployments

Example combined usage:

```groovy
@Library(['jenkins-version', 'jenkins-docker', 'jenkins-pushover']) _

pipeline {
  agent any

  stages {
    stage('Version') {
      steps {
        script {
          def versionInfo = versionLib.determineVersion()
          env.VERSION = versionInfo.cleanVersion
        }
      }
    }

    stage('Build & Deploy') {
      steps {
        script {
          dockerLib.buildImage('myapp')
          dockerLib.deployContainer('myapp', 'myapp', '8080')
          dockerLib.verifyContainer('myapp', env.VERSION, 'localhost', '8080', '/health')
        }
      }
    }
  }

  post {
    success {
      script {
        sendPushoverNotification(
          "✅ myapp v${env.VERSION} deployed successfully",
          "Jenkins",
          0,
          "task-ok"
        )
      }
    }
  }
}
```

## Changelog

### v1.1.0 (2025-11-24)
- **Breaking Change**: Added `host` parameter to `verifyContainer` function
  - Allows verification of containers on different hosts/IPs
  - Required for Jenkins running in containers accessing other containers
  - Update existing calls to include host parameter (e.g., 'localhost')

### v1.0.0 (2025-11-24)
- Initial release
- Added buildImage function
- Added deployContainer function
- Added verifyContainer function
- Added pushToRegistry function
- Added cleanup function
