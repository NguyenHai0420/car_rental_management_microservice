package crm.ms.controller;

import crm.ms.exception.GlobalExceptionHandler;
import crm.ms.service.VehicleMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class VehicleMediaControllerTest {
    @Mock VehicleMediaService media;
    MockMvc mvc;
    final UUID vehicleId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new VehicleMediaController(media, new MediaAccessPolicy("ADMIN,STAFF")))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void imagesArePublic() throws Exception {
        when(media.listImages(vehicleId)).thenReturn(List.of());
        mvc.perform(get(path("/images"))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/documents", "/documents/00000000-0000-0000-0000-000000000001/url"})
    void documentsRequireAuthentication(String suffix) throws Exception {
        mvc.perform(get(path(suffix))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(media);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CUSTOMER", "NOT_ADMIN", "superstaff", ""})
    void roleMatchingUsesWholeNames(String roles) throws Exception {
        mvc.perform(get(path("/documents")).header("X-User-ID", "user").header("X-User-Roles", roles))
                .andExpect(status().isForbidden());
        verifyNoInteractions(media);
    }

    @Test
    void acceptsCaseInsensitiveStaffRole() throws Exception {
        when(media.listDocuments(vehicleId)).thenReturn(List.of());
        mvc.perform(get(path("/documents")).header("X-User-ID", "user").header("X-User-Roles", "customer, staff"))
                .andExpect(status().isOk());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mvc.perform(multipart(path("/images"))
                .file(new MockMultipartFile("file", "car.png", "image/png", new byte[]{1}))
                .param("imageType", "OTHER")).andExpect(status().isUnauthorized());
        verifyNoInteractions(media);
    }

    @Test
    void invalidEnumIsBadRequest() throws Exception {
        mvc.perform(multipart(path("/images"))
                .file(new MockMultipartFile("file", "car.png", "image/png", new byte[]{1}))
                .param("imageType", "INVALID").header("X-User-ID", "user").header("X-User-Roles", "STAFF"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(media);
    }

    private String path(String suffix) {
        return "/api/v1/vehicles/" + vehicleId + suffix;
    }
}
