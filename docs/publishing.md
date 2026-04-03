# Publishing

Artifacts are published under group ID `com.borjaglez` and configured for OSSRH / Maven Central publication.

## Published Modules

| Module | Artifact |
|---|---|
| `spring-boot-cqrs-core` | `com.borjaglez:spring-boot-cqrs-core` |
| `spring-boot-cqrs-boot3-starter` | `com.borjaglez:spring-boot-cqrs-boot3-starter` |
| `spring-boot-cqrs-boot4-starter` | `com.borjaglez:spring-boot-cqrs-boot4-starter` |
| `spring-boot-cqrs-rabbitmq` | `com.borjaglez:spring-boot-cqrs-rabbitmq` |

The `examples/*` modules are NOT published.

## Prerequisites

### 1. Sonatype Central Portal Account

Create an account at [central.sonatype.org](https://central.sonatype.org/) and verify ownership of the `com.borjaglez` namespace.

### 2. Generate a GPG Signing Key

Generate a key (Ed25519 or RSA 4096 are both fine):

```bash
gpg --full-generate-key
```

Export the private key in ASCII-armored format:

```bash
gpg --armor --export-secret-keys TU_EMAIL > private-key.asc
```

Get your key ID (last 8 characters):

```bash
gpg --list-secret-keys --keyid-format SHORT
```

Publish the public key to a key server (required for Maven Central validation):

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
```

### 3. Generate a Deployment Token

In Central Portal, go to **Account > Tokens** and generate a new deployment token. You will receive:

- **Token username**: something like `ot-xxxx~xxxx`
- **Token password**: a long string

Save both -- the password is shown only once.

### 4. Configure GitHub Secrets

Go to your repository **Settings > Secrets and variables > Actions** and add:

| Secret | Value |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | The **username** from your deployment token (e.g., `ot-xxxx~xxxx`) |
| `MAVEN_CENTRAL_PASSWORD` | The **password** from your deployment token |
| `GPG_SIGNING_KEY` | Full content of `private-key.asc` |
| `GPG_SIGNING_PASSWORD` | Your GPG key passphrase |

> **Note**: These are deployment token credentials, not your Central Portal account login. The token name you give (e.g., "github-ci") is only for your own reference.

> **Note**: When you generate a token in Central Portal, you give it a name (e.g., "github-ci") -- that name is only for your own reference. The token **value** (the long string) is what goes in `MAVEN_CENTRAL_PASSWORD`. The username is always your account username, not the token name.

## Publishing Workflows

### Release (manual)

Trigger via **Actions > Release > Run workflow**:

- **version**: stable (`0.1.0`) or pre-release (`0.1.0-beta.1`)
- **branch**: target branch (default: `main`)

The workflow:
1. Validates version format (rejects SNAPSHOTs)
2. Runs `./gradlew quality`
3. Generates `CHANGELOG.md` via `git-cliff` (stable releases only)
4. Commits changelog and creates git tag `v{version}`
5. Auto-bumps `gradle.properties` to next patch+1 SNAPSHOT (stable only)
6. Publishes via `./gradlew publish -Pversion={version}`
7. Creates a GitHub Release

### Snapshot (automatic)

Triggers on every push to `main` or `release/*` branches. Publishes the current SNAPSHOT version from `gradle.properties`.

If the current version is not a SNAPSHOT, it derives the version from the last git tag + 1 patch.

### Feature Snapshot (automatic)

Triggers on pushes to `feat/**` or `feature/**` branches. Derives version from the base version + sanitized branch name. Example: `feat/my-feature` on `0.1.0-SNAPSHOT` becomes `0.1.0-my-feature-SNAPSHOT`.

## Local Publishing

If you need to publish outside CI:

```bash
export ORG_GRADLE_PROJECT_mavenCentralUsername=TU_USERNAME
export ORG_GRADLE_PROJECT_mavenCentralPassword=TU_TOKEN
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat private-key.asc)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=TU_PASSPHRASE

./gradlew publish
```

Note: signing is only enforced when the `CI` environment variable is set. Local dev builds skip signing.

## GPG Key Renewal

Keys can be extended before expiration:

```bash
gpg --edit-key TU_EMAIL
> expire
# Choose a new expiration date
> save
```

Then re-export and re-publish the public key:

```bash
gpg --armor --export TU_EMAIL | gpg --import
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
```

Update the `GPG_SIGNING_KEY` GitHub secret with the new exported key if the key material changed.

## Developer Metadata

Developer is set to `Borja Gonzalez Enriquez` (`borjaglez`).
