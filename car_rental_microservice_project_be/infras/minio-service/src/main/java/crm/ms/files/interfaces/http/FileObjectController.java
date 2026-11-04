package crm.ms.files.interfaces.http;

import crm.ms.files.application.ClientScope;
import crm.ms.files.application.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/internal/v1/files")
public class FileObjectController {
    private final FileService files;

    public FileObjectController(FileService files) {
        this.files = files;
    }

    @PutMapping("/object")
    public ResponseEntity<Void> put(@RequestParam String bucket, @RequestParam String key,
                                    @RequestHeader(value = "Content-Type", required = false) String contentType,
                                    HttpServletRequest request) throws IOException {
        try (var input = request.getInputStream()) {
            files.put(client(request), bucket, key, input, request.getContentLengthLong(), contentType);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/object")
    public ResponseEntity<Void> delete(@RequestParam String bucket, @RequestParam String key, HttpServletRequest request) {
        files.delete(client(request), bucket, key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public-url")
    public ResponseEntity<PublicLink> publicUrl(@RequestParam String bucket, @RequestParam String key, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new PublicLink(files.publicUrl(client(request), bucket, key)));
    }

    @GetMapping("/download-url")
    public ResponseEntity<FileService.DownloadLink> downloadUrl(@RequestParam String bucket, @RequestParam String key,
            @RequestParam long expirySeconds, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(files.downloadUrl(client(request), bucket, key, expirySeconds));
    }

    private ClientScope client(HttpServletRequest request) {
        return (ClientScope) request.getAttribute(ServiceAuthenticationFilter.CLIENT_ATTRIBUTE);
    }

    public record PublicLink(String url) {
    }
}
