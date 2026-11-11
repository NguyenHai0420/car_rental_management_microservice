package crm.ms.service.gateway;

import crm.ms.dto.SepayTransactionsResponse;
import crm.ms.dto.SepayTransactionsResponse.TransactionItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class SepayPollingClient {

    private static final String SEPAY_V2_TRANSACTIONS_URL = "https://userapi.sepay.vn/v2/transactions";
    private static final String SEPAY_V1_TRANSACTIONS_URL = "https://my.sepay.vn/userapi/transactions/list";

    private final RestClient restClient;
    private final String apiToken;
    private final String accountNumber;

    public SepayPollingClient(
            @Value("${payment.gateway.sepay.api-token:}") String apiToken,
            @Value("${payment.gateway.vietqr.account-no:}") String accountNumber) {
        this.apiToken = apiToken != null ? apiToken.trim() : "";
        this.accountNumber = accountNumber != null ? accountNumber.trim() : "";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(4));

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public boolean isConfigured() {
        return !apiToken.isBlank();
    }

    /**
     * Chủ động hỏi SePay API (v2 trước, fallback v1) để kiểm tra giao dịch tiền vào mới nhất mà không cần ngrok.
     */
    public List<TransactionItem> fetchIncomingTransactions(String paymentCode) {
        if (!isConfigured()) {
            return List.of();
        }

        try {
            URI v2Uri = UriComponentsBuilder.fromUriString(SEPAY_V2_TRANSACTIONS_URL)
                    .queryParam("transfer_type", "in")
                    .queryParam("per_page", 20)
                    .build()
                    .encode()
                    .toUri();

            SepayTransactionsResponse v2Response = callSepayGet(v2Uri);
            if (v2Response != null && v2Response.getData() != null) {
                return v2Response.getData();
            }
        } catch (Exception ex) {
            log.debug("SePay API v2 query unavailable ({}), falling back to API v1", ex.getMessage());
        }

        try {
            UriComponentsBuilder v1Builder = UriComponentsBuilder.fromUriString(SEPAY_V1_TRANSACTIONS_URL)
                    .queryParam("limit", 20);
            if (!accountNumber.isBlank()) {
                v1Builder.queryParam("account_number", accountNumber);
            }

            SepayTransactionsResponse v1Response = callSepayGet(v1Builder.build().encode().toUri());
            if (v1Response != null && v1Response.getTransactions() != null) {
                return v1Response.getTransactions();
            }
        } catch (Exception ex) {
            log.warn("Failed to poll SePay transactions for paymentCode={}: {}", paymentCode, ex.getMessage());
        }

        return List.of();
    }

    private SepayTransactionsResponse callSepayGet(URI uri) {
        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(SepayTransactionsResponse.class);
    }
}

