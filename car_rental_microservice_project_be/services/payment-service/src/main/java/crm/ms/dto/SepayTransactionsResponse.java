package crm.ms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SepayTransactionsResponse {
    private List<TransactionItem> data;
    private List<TransactionItem> transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionItem {
        private String id;

        @JsonProperty("bank_brand_name")
        private String bankBrandName;

        @JsonProperty("account_number")
        private String accountNumber;

        @JsonProperty("transaction_date")
        private String transactionDate;

        @JsonProperty("amount_in")
        private BigDecimal amountIn;

        @JsonProperty("amount_out")
        private BigDecimal amountOut;

        @JsonProperty("transaction_content")
        private String transactionContent;

        @JsonProperty("reference_number")
        private String referenceNumber;

        private String code;

        @JsonProperty("transfer_type")
        private String transferType;
    }
}

