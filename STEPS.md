# Running the PoC with Claude Code, step by step

You need: git, Java 21+, Node 20+, Claude Code, and Flutter (only for the mobile app).

## 1. Set up the repo

```bash
cd checkout-poc
git init -b main
cp .env.example .env          # then put a random value in it: openssl rand -hex 32
```

## 2. Review the contract (your part)

Read `contract/states.md`, `contract/checkout-api.yaml` and `contract/provider-api.yaml`.
Change anything you disagree with in your own editor. Claude can't edit these files: the
deny rules in `.claude/settings.json` apply to every Claude session in this repo, yours too.

Then commit everything:

```bash
git add .
git commit -m "Contract, rules and agent setup"
```

## 3. Trust the folder once

```bash
claude        # accept the trust prompt, then type /exit
```

## 4. Start all five agents, one terminal each

Start them all now, before you merge anything. Each one gets its own worktree, which
branches from main as it is now, so none of them can see another's code. This matters
most for test-writer.

```bash
claude --worktree tests    --agent test-writer
claude --worktree provider --agent provider-sim
claude --worktree backend  --agent backend-dev
claude --worktree web      --agent react-dev
claude --worktree mobile   --agent flutter-dev     # optional
```

Each agent starts working straight away. File edits are accepted automatically. Commands
not on the allow list (e.g. curl, installing tools) will ask you first.

If an agent asks to read files outside its folder, say no.
If an agent says the contract is wrong: you decide. Fix it on main yourself, commit, and tell
each agent to run `git merge main`.

## 5. Review and merge each branch yourself

Branches are named `worktree-<name>`. For each one, from the repo folder:

```bash
git diff --stat main...worktree-backend                      # only backend/ should change
git diff main...worktree-backend -- contract CLAUDE.md .claude   # must be empty
```

Read the money code yourself: the callback handler, the status rules and the scheduled job.
Then merge, in this order:

```bash
git merge worktree-provider
git merge worktree-backend
git merge worktree-tests
git merge worktree-web
git merge worktree-mobile
```

## 6. Run it

One terminal each:

```bash
set -a; source .env; set +a; cd dummy-provider && ./mvnw spring-boot:run
set -a; source .env; set +a; cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=fast
cd web && npm install && npm run dev                       # http://localhost:5173
cd mobile && flutter run -d chrome --web-port 5174         # optional
```

Invest an amount in the app and watch the status change.

## 7. Break it on purpose

Turn on chaos, then invest again:

```bash
curl -X PUT localhost:8081/admin/chaos -H 'Content-Type: application/json' \
  -d '{"modes":["duplicate","reverse"],"delaySeconds":1}'
```

Try `drop`, `badSignature`, `fail`, and `{"modes":["cancelTooLate"],"delaySeconds":15}`.
Check what happened with `curl localhost:8080/checkouts/<id>/events`.

## 8. Run the independent tests

With both services still running:

```bash
cd verification && ./mvnw test
```

## 9. Clean up

When you close each Claude session it asks whether to keep its worktree. Once a branch is
merged you can remove it. To clean up later:

```bash
git worktree list
git worktree remove .claude/worktrees/backend
```

## Keep notes

Write down what the agents got wrong and which check caught it (your review, the agent's
own tests, or the independent tests). That is the most useful material for your application.
