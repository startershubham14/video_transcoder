-- Persist the S3 multipart uploadId so the upload-timeout reaper can AbortMultipartUpload an
-- abandoned upload. S3 emits no event for a failed/abandoned upload, so detection is server-driven
-- (the reaper), and aborting needs the uploadId — which the upload flow previously did not store.
ALTER TABLE jobs ADD COLUMN upload_id TEXT;
