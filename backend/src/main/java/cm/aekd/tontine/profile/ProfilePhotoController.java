package cm.aekd.tontine.profile;

import cm.aekd.tontine.contribution.StoredResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Photo de profil : chacun gère la sienne ; toutes sont visibles par les
 * utilisateurs connectés.
 */
@RestController
public class ProfilePhotoController {

    private final ProfilePhotoService service;

    public ProfilePhotoController(ProfilePhotoService service) {
        this.service = service;
    }

    @GetMapping("/api/profile/photo")
    public ResponseEntity<InputStreamResource> download() {
        return image(service.download());
    }

    @GetMapping("/api/members/{memberId}/photo")
    public ResponseEntity<InputStreamResource> memberPhoto(@PathVariable UUID memberId) {
        return image(service.downloadForMember(memberId));
    }

    private static ResponseEntity<InputStreamResource> image(StoredResource photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noCache())
                .body(new InputStreamResource(photo.content()));
    }

    @PostMapping(value = "/api/profile/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@RequestParam("file") MultipartFile file) {
        service.upload(file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/profile/photo")
    public ResponseEntity<Void> delete() {
        service.delete();
        return ResponseEntity.noContent().build();
    }
}
