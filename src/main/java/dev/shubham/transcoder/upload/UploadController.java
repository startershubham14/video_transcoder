package dev.shubham.transcoder.upload;

import dev.shubham.transcoder.upload.dto.CompleteUploadRequest;
import dev.shubham.transcoder.upload.dto.CreateUploadRequest;
import dev.shubham.transcoder.upload.dto.CreateUploadResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Upload lifecycle endpoints. The API stays thin: it creates rows and hands out presigned
 * URLs, never touching video bytes. Admission control ({@code 429}) fronts {@code /uploads}.
 */
@RestController
public class UploadController {

    private final UploadHandler uploadHandler;

    public UploadController(UploadHandler uploadHandler) {
        this.uploadHandler = uploadHandler;
    }

    @PostMapping("/uploads")
    public CreateUploadResponse createUpload(@Valid @RequestBody CreateUploadRequest request) {
        return uploadHandler.createUpload(request.filename(), request.sizeBytes(), request.contentType());
    }

    @PostMapping("/jobs/{id}/complete")
    public ResponseEntity<Void> completeUpload(@PathVariable("id") UUID jobId,
                                               @Valid @RequestBody CompleteUploadRequest request) {
        uploadHandler.completeUpload(jobId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build(); // 202 PREPARING
    }
}
