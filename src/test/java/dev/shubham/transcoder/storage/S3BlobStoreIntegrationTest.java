package dev.shubham.transcoder.storage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the S3 adapter against a real S3-compatible store (Testcontainers MinIO) —
 * the storage port is critical and otherwise only exercised by manual Docker runs. Exercises the
 * real upload/download/exists/size paths, presigned GET (fetched over HTTP), the public URL, and a
 * full presigned multipart round-trip (initiate → PUT part → complete).
 */
@Testcontainers
class S3BlobStoreIntegrationTest {

    private static final String BUCKET = "test-bucket";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Container
    static MinIOContainer MINIO = new MinIOContainer("minio/minio:latest");

    static S3BlobStore store;
    static String endpoint;

    @BeforeAll
    static void setUp() {
        endpoint = MINIO.getS3URL();
        URI uri = URI.create(endpoint);
        var creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword()));
        S3Client s3 = S3Client.builder()
                .endpointOverride(uri).region(Region.US_EAST_1).credentialsProvider(creds)
                .forcePathStyle(true).build();
        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(uri).region(Region.US_EAST_1).credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        s3.createBucket(b -> b.bucket(BUCKET));

        var props = new StorageProperties("us-east-1",
                new StorageProperties.S3(BUCKET, endpoint, 5L * 1024 * 1024, endpoint));
        store = new S3BlobStore(s3, presigner, props);
    }

    @Test
    void uploadDownloadExistsAndSize(@TempDir Path dir) throws Exception {
        byte[] data = "hello object storage".getBytes();
        Path src = Files.write(dir.resolve("in.bin"), data);
        String key = "up/obj.bin";

        store.upload(src, key);

        assertTrue(store.exists(key));
        assertFalse(store.exists("up/missing.bin"));
        assertEquals(data.length, store.objectSize(key));

        Path out = dir.resolve("out.bin");
        store.download(key, out);
        assertArrayEquals(data, Files.readAllBytes(out));
    }

    @Test
    void presignedGetIsFetchableOverHttp(@TempDir Path dir) throws Exception {
        byte[] data = "presigned download".getBytes();
        String key = "get/obj.bin";
        store.upload(Files.write(dir.resolve("g.bin"), data), key);

        URL url = store.presignGet(key, TTL);
        HttpResponse<byte[]> resp = HTTP.send(
                HttpRequest.newBuilder(url.toURI()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        assertArrayEquals(data, resp.body());
    }

    @Test
    void publicUrlIsPathStyle() {
        assertEquals(endpoint + "/" + BUCKET + "/some/key.mp4", store.publicUrl("some/key.mp4").toString());
    }

    @Test
    void presignedMultipartRoundTrip() throws Exception {
        String key = "mp/obj.bin";
        byte[] data = "multipart upload body".getBytes();

        PresignedMultipartUpload upload = store.initiateMultipartUpload(key, 1, TTL);
        assertEquals(1, upload.partUrls().size());

        HttpResponse<Void> put = HTTP.send(
                HttpRequest.newBuilder(upload.partUrls().get(0).toURI())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(data)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, put.statusCode());
        String etag = put.headers().firstValue("ETag").orElseThrow();

        store.completeMultipartUpload(key, upload.uploadId(), List.of(etag));

        assertTrue(store.exists(key));
        assertEquals(data.length, store.objectSize(key));
    }

    @Test
    void abortMultipartLeavesNoObject() {
        String key = "ab/obj.bin";
        PresignedMultipartUpload upload = store.initiateMultipartUpload(key, 1, TTL);

        store.abortMultipartUpload(key, upload.uploadId()); // must not throw

        assertFalse(store.exists(key)); // never completed → nothing lingers
    }
}
