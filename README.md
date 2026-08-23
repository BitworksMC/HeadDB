# HeadDB

> **Thousands of custom Minecraft heads—instantly accessible in‑game!**

---

## 📦 Table of Contents
1. [Features](#features)
2. [Download & Installation](#download--installation)
3. [Permissions](#permissions)
4. [Reporting Issues](#reporting-issues)
5. [Using the API](#using-the-api)
   - [Adding the Dependency](#adding-the-dependency)
   - [Obtaining the API](#obtaining-the-api)
   - [Waiting for Database Ready](#waiting-for-database-ready)
   - [Examples](#examples)
6. [API Reference](#api-reference)
7. [Local Paper Test Server](#local-paper-test-server)
8. [Contributing](#contributing)
9. [License](#license)

---

## 🔍 Features
- **Massive Head Library**  
  Browse thousands of player heads, from popular themes to custom community submissions.  
- **Lightweight API**  
  Decoupled `headdb-api` module keeps your plugin lean—no extra dependencies at runtime.  
- **Async Loading**  
  The database loads on a background thread.  
- **Flexible Querying**  
  Search by name, ID, category, or tags.
  Structured queries also support multiple IDs and all/any matching.
- **Operational controls**
  Inspect catalog health, synchronize immediately, reload runtime settings,
  inspect held heads, and browse recently assigned IDs.
- **Network-ready player storage**
  Keep player settings in local SQLite or share favorites, language, and sound
  preferences through MySQL.
- **Paper and Folia menus**
  Modern inventories are isolated per open and their registries are safe across
  region threads. The legacy jar continues to support pre-1.21 Bukkit servers.
- **Website handoff**
  `/hdb submit` opens the public submission form, and in-game searches can link
  directly to the equivalent browser search for richer filtering and copyable commands.

Browse the catalog, submit heads, and read the plugin and HTTP API documentation at
[headdb.net](https://headdb.net). The modern plugin restores its saved catalog,
then polls the managed revision feed for additions, edits, and removals. A complete
snapshot and the legacy BitworksMC GitHub catalog remain available for recovery.

---

## 🚀 Download & Installation

HeadDB 6.1.0 is distributed as two server-specific jars. Install exactly one:

| File | Server versions | Java | Purpose |
|---|---|---|---|
| `HeadDB-6.1.0.jar` | Paper/Folia 1.21.0 and newer | Java 21+ | The full modern plugin and the recommended download. |
| `HeadDB-6.1.0-legacy.jar` | Bukkit-compatible 1.8.8-1.20.6 | Java 8 bytecode* | The isolated implementation for servers before 1.21. |

\* Use the Java version required by the Minecraft server. The legacy plugin
itself is Java 8-compatible, but later Minecraft releases require newer Java
runtimes (for example, Java 17 or Java 21).

Do not install both jars on the same server. The old `-Spigot.jar` classifier no
longer exists: the modern artifact uses Paper APIs, while the legacy artifact is
compiled against the 1.8.8 Spigot API, is compatibility-tested through the
1.20.6 API, and does not contain `api-version` or a modern library-loader
declaration. The modern artifact is compiled against Paper 1.21 and declares
`api-version: 1.21`, allowing it to run on later Paper releases.

The legacy jar includes the normal HeadDB browsing, search, favorites, local
heads, custom-category, purchase, player-storage, API, localization, sound,
update-checking, metrics, and database-refresh features. Its menus are implemented
with Bukkit inventories so they work before Paper's modern menu APIs. Purchase
amounts use inventory presets, and advanced MiniMessage effects are simplified
on older clients. The modern jar declares Folia support and avoids sharing menu
inventories between opens; page and navigation registries use concurrent state.

HeadDB checks GitHub Releases for updates on startup and every 24 hours by
default. Console notifications and player notifications can be configured under
`updateChecker` in `config.yml`; players require `headdb.update.notify`. The
download link is clickable on Paper and displayed as a plain URL on Spigot.

Choose your preferred source:

- **Releases (GitHub)**  
  https://github.com/BitworksMC/HeadDB/releases  
- **Modrinth**  
  https://modrinth.com/plugin/headdb
- **Hangar (PaperMC)**  
  https://hangar.papermc.io/GoodrichDev/HeadDatabase
- **Spigot** *(Not recommended)*  
  https://www.spigotmc.org/resources/headdb.133362/

---

## 🔐 Permissions

| Permission | Purpose |
|---|---|
| `headdb.command.open` | Open HeadDB and its category menus. |
| `headdb.command.search` | Search the database. |
| `headdb.command.give` | Give a database head by command. |
| `headdb.command.info` | View HeadDB and server version information. |
| `headdb.command.sounds` | Toggle personal HeadDB interface sounds with `/hdb sounds`. |
| `headdb.command.submit` | Show the clickable headdb.net submission link with `/hdb submit`. |
| `headdb.command.status` | Show catalog source, revision, size, and last synchronization result. |
| `headdb.command.sync` | Run a managed-catalog synchronization immediately. |
| `headdb.command.reload` | Reload runtime messages, menus, prices, links, sounds, and categories. |
| `headdb.command.inspect` | Inspect a held or identified head and open its website record. |
| `headdb.command.recent` | Browse the catalog entries with the newest IDs. |
| `headdb.command.language` | Select a personal message language, such as `en` or `es`. |
| `headdb.update.notify` | Receive a notification with the latest-release download link. |
| `headdb.category.*` | Access every category. |
| `headdb.category.<category_id>` | Access one database or custom category. |
| `headdb.category.local` | Access heads generated from players known to this server. |
| `headdb.category.custom` | Open the custom-categories menu. |
| `headdb.category.favorites` | Open favorites (`headdb.favorites` is a legacy alias). |
| `headdb.admin` | Grant all HeadDB commands and categories. |

To grant every category except local heads, grant `headdb.category.*` and explicitly deny `headdb.category.local`. Category-specific values take precedence over the wildcard, so a LuckPerms setup can use:

```text
/lp group <group> permission set headdb.category.* true
/lp group <group> permission set headdb.category.local false
```

Database category IDs are their lowercase names with spaces and symbols replaced by underscores. For example, `Food & Drinks` uses `headdb.category.food_drinks`. Custom categories use the identifier from `categories.yml` (normalized the same way).

Common command forms include `/hdb give id:123`, `/hdb give 16 id:123`, and
the administrator form `/hdb give <player> <amount> <head>`. Structured search
accepts quoted filters, multiple `id:` values, and `--any`, for example:

```text
/hdb search category:"Food & Drinks" tag:dessert id:123 id:456 --any
```

Set `storage.player.backend` to `MYSQL` on every proxy-network server and use the
same JDBC credentials to share player preferences. Changing the storage backend,
database endpoints, worker counts, or scheduled intervals requires a restart;
other settings can be applied with `/hdb reload`.
An empty MySQL table is seeded once from an existing local SQLite player
database, so retain the local file until the first successful migration.

---

## 🐞 Reporting Issues

Found a bug or have a feature request? Open an issue:

[HeadDB Issue Tracker](https://github.com/BitworksMC/HeadDB/issues)

---

## 🤝 Using the API

HeadDB 6.1.0 uses two APIs for different purposes:

- The **HeadDB HTTP API** at `https://headdb.net/api/v1` is the managed source
  for published head data. The modern plugin downloads
  `/catalog/snapshot` when it needs a complete database and polls
  `/catalog/changes` with its saved revision for additions, edits, and removals.
  Server owners normally do not need to call these endpoints themselves. The
  HTTP API documentation is available at
  [headdb.net/docs/api](https://headdb.net/docs/api).
- The **Bukkit Java API** described below is registered by the installed HeadDB
  plugin. Other plugins use it to search the locally synchronized catalog and
  create head items without making their own HTTP requests.

The Java API remains the normal integration point for another Minecraft
plugin. The new HTTP API changes where HeadDB obtains and synchronizes its data;
it does not remove or replace the Bukkit service.

### 1. Adding the Dependency

HeadDB publishes its API module via our own Nexus Maven Repo.

#### Maven
```xml
<repositories>
    <repository>
        <id>bitworks-repo</id>
        <url>https://nexus.tinydc.net/repository/maven-releases/</url>
    </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.bitworksmc</groupId>
    <artifactId>headdb-api</artifactId>
    <version>VERSION</version>
  </dependency>
</dependencies>
```

#### Gradle
```gradle
repositories {
    mavenCentral()
    maven { url 'https://nexus.tinydc.net/repository/maven-releases/' }
}

dependencies {
    implementation "com.bitworksmc:headdb-api:VERSION"
}
```

---

### 2. Obtaining the API

HeadDB’s main `HeadAPI` is registered with Bukkit’s Services Manager:

```java
RegisteredServiceProvider<HeadAPI> rsp = Bukkit.getServicesManager().getRegistration(HeadAPI.class);
if (rsp == null) {
    // HeadDB is not installed or failed to register
    return;
}
HeadAPI api = rsp.getProvider();
```

---

### 3. Waiting for Database Ready

The head database loads asynchronously. Use these methods to wait on it:

```java
// Check if ready without blocking
boolean ready = api.isReady();

// Block until initial load completes
api.awaitReady();

// Asynchronously wait; returns CompletableFuture<List<Head>>
api.onReady().thenAccept(headList -> {
    System.out.println("Loaded " + headList.size() + " heads!");
});
```

---

### 4. Examples

```java
api.onReady().thenAccept(heads -> {
    System.out.println("Total heads: " + heads.size());
    api.findByCategory("Alphabet")
       .thenAccept(catHeads -> System.out.println("Alphabet category: " + catHeads.size()));
});

api.onReady().thenRun(() -> {
    api.findById(1).thenAccept(optHead -> {
        optHead.ifPresentOrElse(
            head -> System.out.println("Head #1: " + head.getName()),
            ()   -> System.out.println("No head with ID 1 found")
        );
    });

    api.findByTexture("cbc826aaafb8dbf67881e68944414f13985064a3f8f044d8edfb4443e76ba")
       .thenAccept(optHead -> {
           optHead.ifPresentOrElse(
               head -> System.out.println("Texture match: " + head.getName()),
               ()   -> System.out.println("No head for that texture")
           );
       });
});
```

---

## 📖 API Reference

All available methods live in the [HeadAPI class on GitHub](https://github.com/BitworksMC/HeadDB/blob/master/headdb-api/src/main/java/com/bitworksmc/headdb/api/HeadAPI.java).

Legacy compatibility: `com.github.thesilentpro.headdb.api.*` remains available and deprecated for migration.

| Method                                    | Description                                                      |
|-------------------------------------------|------------------------------------------------------------------|
| `void awaitReady()`                       | Blocks until the database finishes initial load.                 |
| `boolean isReady()`                       | Returns true once a successful database snapshot is available.  |
| `CompletableFuture<List<Head>> onReady()` | Async callback once initial load completes.                      |
| `searchByName(String name, boolean lenient)` | Fuzzy or exact name searches.                                 |
| `findById(int id)`                        | Lookup by internal head ID.                                      |
| `findByTexture(String texture)`           | Lookup by skin texture hash.                                     |
| `findByCategory(String category)`         | Get all heads in a given category.                               |
| `findByTags(String... tags)`              | Get heads matching any of the supplied tags.                     |
| `getHeads()`                              | Retrieve the full list of loaded heads (async).                  |
| `computeLocalHeads()`                     | Generate `ItemStack`s for all players known to the server.       |
| `computeLocalHead(UUID uniqueId)`         | Generate an `ItemStack` for a specific player UUID.              |
| `List<String> findKnownCategories()`      | List all category names.                                         |
| `CatalogStatus getCatalogStatus()`        | Read source, revision, size, and last synchronization health.     |
| `search(SearchQuery query)`               | Structured, sorted, paginated local catalog search.               |
| `addCatalogUpdateListener(listener)`      | Observe added, edited, and removed catalog IDs.                   |
| `ExecutorService getExecutor()`           | Access the internal executor for advanced workflows.             |

---

## 🧪 Local Paper Test Server

On Windows with Java 25 and Maven, run this command from the repository root:

```powershell
mvn -pl headdb-core -am -Pdev-server verify
```

For an IntelliJ Maven run configuration, use the repository root as the working
directory and `-pl headdb-core -am -Pdev-server verify` as the command line.
The profile builds and tests HeadDB, downloads the latest stable Paper build for
Minecraft 26.2, verifies its checksum, installs the Paper plugin, and keeps the
server attached to the IDE console. Connect to `localhost` in Minecraft, and
enter `stop` in the console for a graceful shutdown.

The server, world, and configuration persist in the ignored
`build/dev-server` directory. The Paper download is reused until a newer stable
build is available, while `plugins/HeadDB.jar` is replaced on every launch. For
faster iterations after an already-tested change, add `-DskipTests`. Memory can
be overridden with `-Ddev.server.xms=1G -Ddev.server.xmx=4G`.

Running this profile writes `eula=true`. By using it, you are confirming that
you agree to the [Minecraft EULA](https://aka.ms/MinecraftEULA).

---

## Building both release jars

Run `mvn clean package` from the repository root. The release files are written
to:

- `headdb-core/target/HeadDB-6.1.0.jar`
- `headdb-legacy/target/HeadDB-6.1.0-legacy.jar`

The legacy module uses `--release 8`; the modern module uses `--release 21`.
Maven may run on a newer JDK when building both artifacts together.

---

## 🤗 Contributing

1. Fork the repository  
2. Create a feature branch (`git checkout -b feature/YourFeature`)  
3. Commit your changes (`git commit -m "Add awesome feature"`)  
4. Push to your branch (`git push origin feature/YourFeature`)  
5. Open a Pull Request  

Please follow the existing code style.

---

## 📜 License

Distributed under the [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html).
