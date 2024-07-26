package com.flagsmith.interfaces;

import com.flagsmith.config.FlagsmithConfig;
import com.flagsmith.flagengine.environments.EnvironmentModel;
import com.flagsmith.flagengine.identities.traits.TraitModel;
import com.flagsmith.models.Flags;
import com.flagsmith.threads.RequestProcessor;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

public interface FlagsmithSdk {

  Flags getFeatureFlags(boolean doThrow);

  Flags identifyUserWithTraits(
      String identifier, List<? extends TraitModel> traits, boolean isTransient, boolean doThrow
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
   *
   * @param identifier user identifier
   */
  default void assertValidUser(String identifier) {
    if (identifier == null || StringUtils.isBlank(identifier)) {
      throw new IllegalArgumentException("Missing user identifier");
    }
  }
}
