# GitHub Actions Runner Troubleshooting

## What was fixed in the repository

The original repository had five workflow files, and every file listened to both `push` and `pull_request`. Because development is performed through many small GitHub commits, that design created duplicate workflow runs and an unnecessary job storm.

The workflows are now consolidated into `.github/workflows/ci.yml` with:

- one pull-request run for development branches
- one push run only when `main` changes
- manual `workflow_dispatch`
- concurrency cancellation for superseded runs
- explicit Gradle 9.1.0 setup
- Java 21 for the platform-independent core
- Java 25 for the ATM11 NeoForge adapter
- separate jobs for the API, Discord bot, website, core, and adapter

## Current external blocker

GitHub accepts the workflow and creates all five jobs, but each job fails before its first step. GitHub returns no step list and no downloadable job log. This means the runner is not starting; it is not a normal Java, Python, Node, Gradle, or test failure.

The repository cannot repair an account-level or GitHub-hosted runner restriction from source code.

## Checks for the repository owner

1. Open the repository on GitHub.
2. Open **Actions** and select the newest **CI** run.
3. Look for a red banner or annotation above the job list. Copy the exact message into the development chat.
4. Open **Settings → Actions → General**.
5. Confirm GitHub Actions is enabled and that the repository is allowed to use:
   - `actions/checkout`
   - `actions/setup-java`
   - `actions/setup-python`
   - `actions/setup-node`
   - `actions/upload-artifact`
   - `gradle/actions/setup-gradle`
6. Open the account's **Settings → Billing and licensing** pages and check Actions usage, budgets, payment problems, and account restrictions.
7. Check the account email and GitHub notifications for a security, abuse, or Actions restriction notice.
8. After resolving the displayed restriction, use **Re-run all jobs** on the newest consolidated CI run.

## Local verification

Run:

```bash
./scripts/verify-local.sh
```

This validates Python, JavaScript, and Java components on a development machine with the required tools. The NeoForge build additionally needs Java 25, Gradle 9.1+, dependency-download access, and enough memory.

## Private-server verification

A successful CI build proves that code compiles and automated tests pass. It does not prove that the mod works inside ATM11. Install the generated JAR only on a disposable private test server and follow `docs/ATM11_PRIVATE_TEST_PLAN.md` before any public launch.
