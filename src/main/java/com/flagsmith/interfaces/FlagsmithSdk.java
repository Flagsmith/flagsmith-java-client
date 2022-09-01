package com.flagsmith.interfaces;

import com.fasterxml.jackson.databind.JsonNode;
import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.features.FeatureStateModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.models.Flags;
import com.flagsmith.threads.RequestProcessor;
import java.util.List;
import lombok.NonNull;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

public interface FlagsmithSdk {

  Flags getFeatureFlags(boolean doThrow);

  Flags identifyUserWithTraits(
      String identifier, List<TraitModel> traits, boolean doThrow
  );

  FlagsmithConfig getConfig();
  
  EnvironmentModel getEnvironment();

  RequestProcessor getRequestor();

  Request newGetRequest(HttpUrl url);

  Request newPostRequest(HttpUrl url, RequestBody body);

  void close();

  // Cache
  default FlagsmithCache getCache() {
    return null;
  }

  /**
   * validate user has a valid identifier.
   * @param identifier user identifier
   */
  default void assertValidUser(@NonNull String identifier) {
    if (StringUtils.isBlank(identifier)) {
      throw new IllegalArgumentException("Missing user identifier");
    }
  }
}
