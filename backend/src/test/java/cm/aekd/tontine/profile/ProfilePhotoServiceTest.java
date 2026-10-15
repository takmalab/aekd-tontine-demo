package cm.aekd.tontine.profile;

import cm.aekd.tontine.contribution.StoredResource;
import cm.aekd.tontine.member.Member;
import cm.aekd.tontine.member.MemberRepository;
import cm.aekd.tontine.user.User;
import cm.aekd.tontine.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfilePhotoServiceTest {

    @Autowired
    private ProfilePhotoService service;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User login(String email) {
        User user = userRepository.save(new User(email, "x"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBRE"))));
        return user;
    }

    @Test
    void userUploadsReplacesAndDeletesOwnPhoto() throws Exception {
        User user = login("photo-owner@test.aekd.cm");
        assertThatThrownBy(() -> service.download()).hasMessageContaining("Aucune photo");

        service.upload(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3}));
        StoredResource photo = service.download();
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        assertThat(photo.content().readAllBytes()).containsExactly(1, 2, 3);

        service.upload(new MockMultipartFile("file", "photo.png", "image/png", new byte[]{4, 5}));
        assertThat(service.download().content().readAllBytes()).containsExactly(4, 5);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPhotoContentType()).isEqualTo("image/png");

        service.delete();
        assertThat(userRepository.findById(user.getId()).orElseThrow().hasPhoto()).isFalse();
    }

    @Test
    void onlyImagesAreAccepted() {
        login("photo-pdf@test.aekd.cm");
        assertThatThrownBy(() -> service.upload(
                new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[]{1})))
                .hasMessageContaining("Format d'image");
    }

    @Test
    void anyLoggedInUserSeesAMembersPhoto() throws Exception {
        User owner = login("photo-member@test.aekd.cm");
        Member member = memberRepository.save(new Member(owner, "Membre Photo", LocalDate.now()));
        service.upload(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{7, 8}));

        login("photo-viewer@test.aekd.cm");
        assertThat(service.downloadForMember(member.getId()).content().readAllBytes()).containsExactly(7, 8);
    }
}
