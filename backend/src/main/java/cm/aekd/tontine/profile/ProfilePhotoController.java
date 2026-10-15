package cm.aekd.tontine.profile;

import cm.aekd.tontine.contribution.StoredResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Photo de profil du compte connecté : tout utilisateur authentifié, pour lui-même uniquement. */
@RestController
@RequestMapping("/api/profile/photo")
public class ProfilePhotoController {

    private final ProfilePhotoService service;

    public ProfilePhotoController(ProfilePhotoService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<InputStreamResource> download() {
        StoredResource photo = service.download();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noCache())
                .body(new InputStreamResource(photo.content()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(@RequestParam("file") MultipartFile file) {
        service.upload(file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> delete() {
        service.delete();
        return ResponseEntity.noContent().build();
    }
}
