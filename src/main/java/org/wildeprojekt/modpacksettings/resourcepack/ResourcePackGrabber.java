package org.wildeprojekt.modpacksettings.resourcepack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.experimental.StandardException;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.loader.api.FabricLoader;
import org.wildeprojekt.modpacksettings.ModpackSettings;
import org.wildeprojekt.modpacksettings.config.ModConfig;
import org.wildeprojekt.modpacksettings.i18n.Messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Downloads the configured resource packs from the latest GitHub releases before Minecraft starts.
 */
@Slf4j
public final class ResourcePackGrabber {

    /** Version of the options.txt activation behaviour last applied by this class. */
    private static final int ACTIVATION_VERSION = 1;

    /** HTTP client connection timeout. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Per-request timeout for GitHub API and asset downloads. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** User agent sent to GitHub API and asset requests. */
    private static final String USER_AGENT = "ModpackSettings/" + ModpackSettings.MOD_ID;

    /**
     * Private constructor for this utility class.
     */
    private ResourcePackGrabber() {
    }

    /**
     * Checks each configured GitHub repository and updates local resource packs when needed.
     *
     * <p>Failures for individual packs are collected; remaining packs still run. If any pack
     * failed, a single {@link ResourcePackGrabberException} lists them after the loop.</p>
     *
     * @throws ResourcePackGrabberException if one or more configured packs failed to update
     */
    public static void run() throws ResourcePackGrabberException {

        try {
            ModConfig config = ModConfig.load();
            List<ModConfig.ResourcePackConfig> resourcePacks = config.getResourcePacks();

            if (resourcePacks == null || resourcePacks.isEmpty()) {
                LOGGER.info("No resource packs configured; skipping.");
                return;
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            Map<String, JsonObject> releaseCache = new HashMap<>();
            List<String> failures = new ArrayList<>();
            boolean dirty = false;

            for (ModConfig.ResourcePackConfig resourcePack : resourcePacks) {
                if (resourcePack == null)
                    continue;

                if (!resourcePack.isEnabled()) {
                    LOGGER.info("Resource pack updater is disabled for {}.", packLabel(resourcePack));
                    continue;
                }

                if (isBlank(resourcePack.getRepository())) {
                    LOGGER.info("Resource pack updater is not configured for {}; skipping.", packLabel(resourcePack));
                    continue;
                }

                try {
                    if (updateOne(client, releaseCache, resourcePack))
                        dirty = true;
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failures.add(formatFailure(resourcePack, exception.getMessage()));
                    LOGGER.warn("Resource pack grabber interrupted for {}.", packLabel(resourcePack), exception);
                    break;
                } catch (Exception exception) {
                    failures.add(formatFailure(resourcePack, exception.getMessage()));
                    LOGGER.warn("Resource pack grabber failed for {}.", packLabel(resourcePack), exception);
                }
            }

            if (dirty)
                config.save();

            if (!failures.isEmpty())
                throw new ResourcePackGrabberException(String.join("\n", failures));

        } catch (ResourcePackGrabberException exception) {
            throw exception;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException)
                Thread.currentThread().interrupt();
            LOGGER.warn("Resource pack grabber failed.", exception);
            throw new ResourcePackGrabberException(exception.getMessage(), exception);
        }
    }

    /**
     * Updates a single resource pack entry: fetch, download if needed, optionally activate.
     *
     * @param client       shared HTTP client
     * @param releaseCache cache of latest-release JSON keyed by repository
     * @param resourcePack pack entry to update
     * @return true when config state for this pack changed and should be saved
     * @throws IOException                  if I/O fails
     * @throws InterruptedException         if an HTTP request is interrupted
     * @throws ResourcePackGrabberException if the update fails
     */
    private static boolean updateOne(HttpClient client, Map<String, JsonObject> releaseCache,
            ModConfig.ResourcePackConfig resourcePack)
            throws IOException, InterruptedException, ResourcePackGrabberException {

        Path partPath = null;
        boolean dirty = false;

        try {
            JsonObject release = getLatestRelease(client, releaseCache, resourcePack.getRepository());
            Instant publishedAt = parsePublishedAt(release);
            String downloadUrl = findAssetDownloadUrl(release, resourcePack.getAssetName());

            Path resourcePacksDir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
            Path targetPath = resourcePacksDir.resolve(resourcePack.getTargetFileName());

            if (!needsDownload(resourcePack.getLastPublishedAt(), publishedAt, targetPath)) {
                LOGGER.info("Resource pack {} is up to date.", resourcePack.getTargetFileName());
                if (shouldBackfillActivation(resourcePack) && activateResourcePack(resourcePack)) {
                    resourcePack.setLastActivatedFileName(resourcePack.getTargetFileName());
                    resourcePack.setActivationVersion(ACTIVATION_VERSION);
                    dirty = true;
                }
                return dirty;
            }

            Files.createDirectories(resourcePacksDir);
            partPath = resourcePacksDir.resolve(resourcePack.getTargetFileName() + ".part");
            Files.deleteIfExists(partPath);
            download(client, downloadUrl, partPath);
            validateZip(partPath);
            replaceTarget(partPath, targetPath);

            resourcePack.setLastPublishedAt(publishedAt.toString());
            dirty = true;

            if (resourcePack.isAutoEnable() && activateResourcePack(resourcePack)) {
                resourcePack.setLastActivatedFileName(resourcePack.getTargetFileName());
                resourcePack.setActivationVersion(ACTIVATION_VERSION);
            }

            LOGGER.info("Updated resource pack {} from release {}.", resourcePack.getTargetFileName(), publishedAt);
            return dirty;

        } catch (Exception exception) {
            deletePart(partPath);
            throw exception;
        }
    }

    /**
     * Returns the latest release for a repository, reusing a cached response when available.
     *
     * @param client       shared HTTP client
     * @param releaseCache cache of latest-release JSON keyed by repository
     * @param repository   GitHub repository in owner/repo format
     * @return latest release JSON object
     * @throws IOException                  if the request fails
     * @throws InterruptedException         if the request is interrupted
     * @throws ResourcePackGrabberException if GitHub returns an error response
     */
    private static JsonObject getLatestRelease(HttpClient client, Map<String, JsonObject> releaseCache, String repository)
            throws IOException, InterruptedException, ResourcePackGrabberException {

        JsonObject cached = releaseCache.get(repository);
        if (cached != null)
            return cached;

        JsonObject release = fetchLatestRelease(client, repository);
        releaseCache.put(repository, release);
        return release;
    }

    /**
     * Builds a short label for logs and failure messages.
     *
     * @param resourcePack pack config
     * @return target filename when set, otherwise a generic label
     */
    private static String packLabel(ModConfig.ResourcePackConfig resourcePack) {
        return isBlank(resourcePack.getTargetFileName()) ? "(unnamed pack)" : resourcePack.getTargetFileName();
    }

    /**
     * Formats a per-pack failure line for the aggregated exception message.
     *
     * @param resourcePack pack that failed
     * @param message      failure detail
     * @return formatted failure line
     */
    private static String formatFailure(ModConfig.ResourcePackConfig resourcePack, String message) {
        String detail = isBlank(message) ? Messages.get("error_unknown") : message;
        return packLabel(resourcePack) + ": " + detail;
    }

    /**
     * Checks whether a string is null, empty, or whitespace only.
     *
     * @param value string to inspect
     * @return true when the string has no non-whitespace characters
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Fetches the latest release JSON from GitHub.
     *
     * @param client     HTTP client to use
     * @param repository GitHub repository in owner/repo format
     * @return latest release JSON object
     * @throws IOException                  if the request fails
     * @throws InterruptedException         if the request is interrupted
     * @throws ResourcePackGrabberException if GitHub returns an error response
     */
    private static JsonObject fetchLatestRelease(HttpClient client, String repository)
            throws IOException, InterruptedException, ResourcePackGrabberException {

        URI uri = URI.create("https://api.github.com/repos/" + repository + "/releases/latest");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new ResourcePackGrabberException(Messages.get("error_release_request_failed", response.statusCode()));

        JsonElement element = JsonParser.parseString(response.body());

        if (!element.isJsonObject())
            throw new ResourcePackGrabberException(Messages.get("error_release_not_json"));

        return element.getAsJsonObject();
    }

    /**
     * Parses the release publication timestamp.
     *
     * @param release GitHub release JSON object
     * @return parsed publication timestamp
     * @throws ResourcePackGrabberException if the timestamp is missing or invalid
     */
    private static Instant parsePublishedAt(JsonObject release) throws ResourcePackGrabberException {

        JsonElement publishedAt = release.get("published_at");
        if (publishedAt == null || publishedAt.isJsonNull())
            throw new ResourcePackGrabberException(Messages.get("error_release_no_published_at"));

        try {

            return Instant.parse(publishedAt.getAsString());

        } catch (DateTimeParseException exception) {

            throw new ResourcePackGrabberException(Messages.get("error_release_bad_published_at"), exception);
        }
    }

    /**
     * Finds the browser download URL for the configured release asset.
     *
     * @param release   GitHub release JSON object
     * @param assetName release asset name to find
     * @return browser download URL for the asset
     * @throws ResourcePackGrabberException if the asset is missing
     */
    private static String findAssetDownloadUrl(JsonObject release, String assetName) throws ResourcePackGrabberException {
        JsonElement assetsElement = release.get("assets");

        if (assetsElement == null || !assetsElement.isJsonArray())
            throw new ResourcePackGrabberException(Messages.get("error_release_no_assets"));

        JsonArray assets = assetsElement.getAsJsonArray();

        for (JsonElement assetElement : assets) {

            if (!assetElement.isJsonObject()) continue;

            JsonObject asset = assetElement.getAsJsonObject();

            if (assetName.equals(getString(asset, "name"))) {

                String url = getString(asset, "browser_download_url");

                if (isBlank(url))
                    throw new ResourcePackGrabberException(Messages.get("error_asset_no_download_url", assetName));

                return url;
            }
        }

        throw new ResourcePackGrabberException(Messages.get("error_asset_not_found", assetName));
    }

    /**
     * Determines whether the resource pack should be downloaded.
     *
     * @param lastPublishedAt last successful release timestamp from config
     * @param publishedAt     latest release timestamp
     * @param targetPath      local target zip path
     * @return true when the zip should be downloaded
     * @throws ResourcePackGrabberException if the saved timestamp is invalid
     */
    private static boolean needsDownload(String lastPublishedAt, Instant publishedAt, Path targetPath)
            throws ResourcePackGrabberException {

        if (Files.notExists(targetPath))
            return true;

        if (isBlank(lastPublishedAt))
            return true;

        try {

            Instant lastDownloaded = Instant.parse(lastPublishedAt);
            return publishedAt.isAfter(lastDownloaded);

        } catch (DateTimeParseException exception) {

            throw new ResourcePackGrabberException(Messages.get("error_saved_timestamp_invalid"), exception);
        }
    }

    /**
     * Determines whether an existing downloaded pack needs a one-time activation backfill.
     *
     * @param resourcePack resource pack updater config
     * @return true when activation should be attempted without a fresh download
     */
    private static boolean shouldBackfillActivation(ModConfig.ResourcePackConfig resourcePack) {

        return resourcePack.isAutoEnable()
                && (!resourcePack.getTargetFileName().equals(resourcePack.getLastActivatedFileName())
                || resourcePack.getActivationVersion() < ACTIVATION_VERSION);
    }

    /**
     * Attempts to enable the configured resource pack in Minecraft options.
     *
     * @param resourcePack resource pack updater config
     * @return true when activation completed without throwing
     */
    private static boolean activateResourcePack(ModConfig.ResourcePackConfig resourcePack) {

        try {

            boolean changed = ResourcePackActivator.activate(resourcePack.getTargetFileName(), resourcePack.isForceLoadIncompatible());

            if (changed)
                LOGGER.info("Enabled resource pack {} in Minecraft options.", resourcePack.getTargetFileName());

            return true;

        } catch (IOException exception) {

            LOGGER.warn("Could not enable resource pack {} in Minecraft options.",
                    resourcePack.getTargetFileName(), exception);

            return false;
        }
    }

    /**
     * Downloads the release asset to a temporary part file.
     *
     * @param client      HTTP client to use
     * @param downloadUrl asset URL to download
     * @param partPath    temporary part file path
     * @throws IOException                  if the download cannot be written
     * @throws InterruptedException         if the request is interrupted
     * @throws ResourcePackGrabberException if the asset response is unsuccessful
     */
    private static void download(HttpClient client, String downloadUrl, Path partPath)
            throws IOException, InterruptedException, ResourcePackGrabberException {

        HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new ResourcePackGrabberException(Messages.get("error_download_failed", response.statusCode()));

        try (InputStream stream = response.body()) {
            Files.copy(stream, partPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Validates that the downloaded file is a non-empty zip file.
     *
     * @param partPath temporary downloaded zip path
     * @throws IOException                  if the file cannot be inspected
     * @throws ResourcePackGrabberException if the zip is empty or invalid
     */
    private static void validateZip(Path partPath) throws IOException, ResourcePackGrabberException {

        if (Files.size(partPath) == 0)
            throw new ResourcePackGrabberException(Messages.get("error_download_empty"));

        try (InputStream stream = Files.newInputStream(partPath)) {

            byte[] magic = stream.readNBytes(4);

            if (magic.length != 4 || magic[0] != 0x50 || magic[1] != 0x4b || magic[2] != 0x03 || magic[3] != 0x04)
                throw new ResourcePackGrabberException(Messages.get("error_download_not_zip"));
        }

        // Empty try-catch is intended - this is a verification for corrupted packages
        //noinspection EmptyTryBlock
        try (ZipFile ignored = new ZipFile(partPath.toFile())) {
            // Opening the zip confirms the central directory can be read.
        } catch (ZipException exception) {
            throw new ResourcePackGrabberException(Messages.get("error_download_corrupt"), exception);
        }
    }

    /**
     * Replaces the existing resource pack with the validated part file.
     *
     * @param partPath   temporary downloaded zip path
     * @param targetPath final resource pack zip path
     * @throws IOException if the file cannot be moved into place
     */
    private static void replaceTarget(Path partPath, Path targetPath) throws IOException {

        Files.deleteIfExists(targetPath);

        try {
            Files.move(partPath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(partPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Deletes a temporary part file, logging but otherwise ignoring clean-up failures.
     *
     * @param partPath temporary part path to delete
     */
    private static void deletePart(Path partPath) {

        if (partPath == null)
            return;

        try {
            Files.deleteIfExists(partPath);
        } catch (IOException exception) {
            LOGGER.warn("Could not delete partial resource pack download {}.", partPath, exception);
        }
    }

    /**
     * Reads a string property from a JSON object.
     *
     * @param object JSON object to read
     * @param name   property name
     * @return property value, or null if the property is missing or null
     */
    private static String getString(JsonObject object, String name) {

        JsonElement element = object.get(name);

        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    /**
     * Exception used to surface resource pack updater failures to prelaunch UI code.
     */
    @StandardException
    public static class ResourcePackGrabberException extends Exception {

        /**
         * Serialization identifier for this checked exception type.
         */
        @Serial
        private static final long serialVersionUID = 1L;

    }
}
