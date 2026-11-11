package crm.ms.controller;

import crm.ms.dto.ResponseApi;
import crm.ms.enums.DocumentType;
import crm.ms.enums.ImageType;
import crm.ms.exception.BusinessException;
import crm.ms.service.VehicleMediaService;
import crm.ms.service.storage.MediaFile;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles/{vehicleId}")
@RequiredArgsConstructor
@Validated
public class VehicleMediaController {
    private final VehicleMediaService media;
    private final VehicleAccessPolicy access;

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi> uploadImage(
            @PathVariable("vehicleId") UUID vehicleId, @RequestPart("file") MultipartFile file,
            @RequestParam("imageType") ImageType imageType, @RequestParam(value = "isPrimary", defaultValue = "false") boolean isPrimary,
            @RequestParam(value = "sortOrder", defaultValue = "0") @Min(0) int sortOrder,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        String actor = access.requireMediaManager(userId, roles);
        try (InputStream input = file.getInputStream()) {
            return response(HttpStatus.CREATED, "Image uploaded", media.uploadImage(vehicleId,
                    upload(file, input), imageType, isPrimary, sortOrder, actor));
        } catch (IOException exception) {
            throw unreadable(exception);
        }
    }

    @GetMapping("/images")
    public ResponseEntity<ResponseApi> images(@PathVariable("vehicleId") UUID vehicleId) {
        return response(HttpStatus.OK, "Vehicle images", media.listImages(vehicleId));
    }

    @DeleteMapping("/images/{imageId}")
    public ResponseEntity<ResponseApi> deleteImage(
            @PathVariable("vehicleId") UUID vehicleId, @PathVariable("imageId") UUID imageId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        access.requireMediaManager(userId, roles);
        media.deleteImage(vehicleId, imageId);
        return response(HttpStatus.OK, "Image deleted", null);
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseApi> uploadDocument(
            @PathVariable("vehicleId") UUID vehicleId, @RequestPart("file") MultipartFile file,
            @RequestParam("docType") DocumentType docType, @RequestParam(value = "docNumber", required = false) @Size(max = 100) String docNumber,
            @RequestParam(value = "issueDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        String actor = access.requireMediaManager(userId, roles);
        try (InputStream input = file.getInputStream()) {
            return response(HttpStatus.CREATED, "Document uploaded", media.uploadDocument(vehicleId,
                    upload(file, input), docType, docNumber, issueDate, expiryDate, actor));
        } catch (IOException exception) {
            throw unreadable(exception);
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<ResponseApi> documents(
            @PathVariable("vehicleId") UUID vehicleId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        access.requireMediaManager(userId, roles);
        return response(HttpStatus.OK, "Vehicle documents", media.listDocuments(vehicleId));
    }

    @GetMapping("/documents/{documentId}/url")
    public ResponseEntity<ResponseApi> documentUrl(
            @PathVariable("vehicleId") UUID vehicleId, @PathVariable("documentId") UUID documentId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        access.requireMediaManager(userId, roles);
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ResponseApi.builder().code(HttpStatus.OK).message("Temporary document URL")
                        .data(media.getDocumentUrl(vehicleId, documentId)).build());
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<ResponseApi> deleteDocument(
            @PathVariable("vehicleId") UUID vehicleId, @PathVariable("documentId") UUID documentId,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {
        access.requireMediaManager(userId, roles);
        media.deleteDocument(vehicleId, documentId);
        return response(HttpStatus.OK, "Document deleted", null);
    }

    private MediaFile upload(MultipartFile file, InputStream input) {
        return new MediaFile(input, file.getSize(), file.getContentType(), file.getOriginalFilename());
    }

    private ResponseEntity<ResponseApi> response(HttpStatus status, String message, Object data) {
        return ResponseEntity.status(status).body(ResponseApi.builder().code(status).message(message).data(data).build());
    }

    private BusinessException unreadable(IOException cause) {
        return new BusinessException(1, 400, "Unable to read uploaded file", cause);
    }
}
