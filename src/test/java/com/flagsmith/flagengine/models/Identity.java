package com.flagsmith.flagengine.models;

import lombok.Data;
import java.time.Instant;
@Data
public class Identity {
    private String identityUuid;
    private String identifier;
    private Instant createdDate;
    private String compositeKey;
    private String environmentApiKey;
    private Integer djangoId;
}
