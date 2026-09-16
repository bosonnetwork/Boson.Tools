# Boson Tools

Command line tools for [Boson](https://github.com/bosonnetwork):

| Tool | For |
|---|---|
| `boson-cli` | Users and developers: register with a super node, manage your profile, passphrase and devices, and work with Boson keys offline |
| `boson-director-cli` | Operators: administer a super node through its Director's admin API - users, devices, plans and features, subscriptions, the blacklist and federation |

Both are built on the Java clients in `boson-director-client`; they hold no protocol code of their own.
They replace the earlier Rust tools of the same names.

## Build

```sh
mvn package
```

Each module leaves a runnable tool in `target/dist`: `bin/boson-cli` (or `bin/boson-director-cli`) and
`lib/`. The launcher runs the JRE bundled beside it when there is one, then `$JAVA_HOME`, then `java` on
the `PATH`; Java 17 or later.

## Getting started

```sh
boson-cli config init --url https://node.example.com:9000
boson-cli identity create
boson-cli user register --name Alice
boson-cli user show
```

On a super node, the setup wizard writes `boson-director-cli`'s configuration for the account that runs
it, so the admin tool works at once:

```sh
sudo -H boson-director-cli user list
```

## Configuration

Both tools read the same YAML format, one setting per line:

```yaml
url: https://node.example.com:9000   # the Director
nodeId: 5vVHp...                     # optional: pins a self-signed certificate, and the token audience
resolve: 127.0.0.1                   # optional: connect here instead of looking up the URL host
identity: user.identity              # optional: the identity file, relative to this file
privateKey: 3Jx...                   # optional: the key itself, instead of an identity file
```

| | `boson-cli` | `boson-director-cli` |
|---|---|---|
| Configuration | `~/.config/boson/client/boson.yaml` | `~/.config/boson/director-cli.yaml` |
| Default identity | `user.identity` beside the configuration | `admin.identity` beside the configuration |

On Windows, `~/.config` is `%APPDATA%`; elsewhere `$XDG_CONFIG_HOME` is honored.

Each setting comes from the first place that has it:

| Setting | Option | Environment |
|---|---|---|
| configuration file | `-c`, `--config` | `BOSON_CONFIG` |
| `url` | `-u`, `--url` | `BOSON_DIRECTOR_URL` |
| `nodeId` | `-n`, `--node-id` | `BOSON_NODE_ID` |
| `resolve` | `--resolve` | `BOSON_DIRECTOR_RESOLVE` |
| `identity` | `-i`, `--identity` | `BOSON_IDENTITY` |
| `privateKey` | - | `BOSON_PRIVATE_KEY` |

then the configuration file, then the default. `config show` shows the settings in effect and where
each comes from.

`config init` and `config set` write the file; they keep its comments, never replace an existing file
with `init`, and refuse to take a private key on the command line, where the shell would keep it in its
history. An identity file holds a Base58 64-byte private key on one line; the tools create identity
files readable by their owner only, never overwrite one, and warn when one is readable by others.

## Commands

Every command follows `<tool> <group> <verb>`, with the same verbs throughout: `list`, `show`, `add`,
`update`, `remove`, and a plain name where the effect differs (`deactivate`, `cancel`, `grant-admin`).
`--help` works at every level, and `help <command>` too.

`boson-cli`:

```
user register | show | update | avatar get | avatar set | passphrase set | passphrase change | passphrase clear
device list | add | remove
node id | status
config init | show | set | unset
identity create | import | show
util keygen | check-key | public-key | sign | hex-to-base58 | base58-to-hex
```

`boson-director-cli`:

```
user list | show | add | update | remove | grant-admin | revoke-admin
device list | show | add | remove
plan list | show | add | update | activate | deactivate
feature list | show | add | update | remove
subscription list | show | active | add | update | cancel
blacklist list | show | add | update | remove
federation node list | show | update | remove
federation service list
federation proposal list | show | remove
federation propose
node id | status
config ... | identity ...
```

List commands show one page (`--page`, `--page-size`) or everything (`--all`), and say how to get the
next page.

## Output, errors and scripting

- Results go to standard output; progress and warnings to standard error. `--json` prints results as
  JSON, with stable field names.
- Errors say what went wrong and, when there is one, how to fix it. `--verbose` adds the details and the
  clients' logging.
- Commands that remove something ask for confirmation on a terminal; in a script, pass `--yes`.
- Passphrases and private keys are never taken as arguments. They are asked for without echo on a
  terminal, or read one per line from standard input:

  ```sh
  printf '%s\n' "$PASSPHRASE" | boson-director-cli user add 5vVHp... --name Bob
  ```

| Exit code | Meaning |
|---|---|
| 0 | Success |
| 1 | The command failed |
| 2 | Invalid command line |
| 3 | Missing or invalid configuration or identity |
| 4 | Not found |
| 5 | Not authorized: identity, passphrase or permission |
| 6 | Already exists |
| 7 | Director unreachable, busy or failing |

## Coming from the Rust tools

| Rust | Java |
|---|---|
| `boson-cli keygen` | `boson-cli util keygen` |
| `boson-cli keychk --ed25519 --private-key KEY` | `boson-cli util check-key --private KEY` |
| `boson-cli sktopk --ed25519 KEY` | `boson-cli util public-key KEY` |
| `boson-cli sign NONCE` | `boson-cli util sign NONCE` |
| `boson-cli hextob58` / `b58tohex` | `boson-cli util hex-to-base58` / `base58-to-hex` |
| `boson-director-cli -k KEY` | `privateKey` in the configuration, `BOSON_PRIVATE_KEY`, or an identity file |
| `rootUserKey` in `director-cli.yaml` | `privateKey` |
| `BOSON_SUPER_NODE_ID` | `BOSON_NODE_ID` |
| `user add --id ID --password P` | `user add ID` (the passphrase is asked for) |
| `device add --user U --device D` | `device add U D` |
| `plan remove` | `plan deactivate` |
| `subscription remove` | `subscription cancel` |
| `blacklist add --node ID` / `--host H` | `blacklist add ID` / `blacklist add H` |
| `federation node-list` / `service-list` / `proposal-list` | `federation node list` / `service list` / `proposal list` |
| interactive pager | `--page`, `--page-size`, `--all` |

`boson-cli util sign` reads the identity from `~/.config/boson/client/user.identity`, where the Rust tool
read `~/.config/boson/user.identity`; pass `--identity` to use another file.

## Testing

```sh
mvn test
```

The tests run the commands against a stub Director. The end-to-end tests of the clients the tools are
built on are maintained with the Director.
