package com.swiftdrop.notification.api;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import com.swiftdrop.notification.api.ParcelStatusChangedListener;
import com.swiftdrop.notification.service.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = InternalOtpNotificationController.class, properties = {
        "INTERNAL_TOKEN=test-internal-token",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/jwks"
})
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
@Import(ParcelStatusChangedListener.class)
class InternalOtpNotificationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SmsSender smsSender;

    // The imported ParcelStatusChangedListener now sends notifications through Notifier.
    @MockitoBean
    private com.swiftdrop.notification.service.Notifier notifier;

    @Test
    void validRequestIsAccepted() throws Exception {
        UUID parcelId = UUID.randomUUID();

        mvc.perform(post("/internal/v1/notifications/otp")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + parcelId + "\",\"code\":\"0123\"}"))
                .andExpect(status().isAccepted());

        verify(smsSender).sendOtp(parcelId, "0123");
    }

    @Test
    void invalidCodeReturnsBadRequest() throws Exception {
        mvc.perform(post("/internal/v1/notifications/otp")
                        .header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + UUID.randomUUID() + "\",\"code\":\"12a4\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingInternalTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/internal/v1/notifications/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelId\":\"" + UUID.randomUUID() + "\",\"code\":\"0123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
