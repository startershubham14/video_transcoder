package dev.shubham.transcoder.storage;

import java.net.URL;
import java.util.List;

/**
 * Result of initiating a multipart upload: the S3 {@code uploadId} (echoed back by the
 * client on complete) and one presigned PUT URL per part, in order.
 */
public record PresignedMultipartUpload(String uploadId, List<URL> partUrls) {
}
