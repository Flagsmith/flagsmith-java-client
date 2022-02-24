package com.flagsmith;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.Flags;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class HelloController {
  private static volatile ObjectMapper mapper = null;

  private static FlagsmithClient flagsmith = FlagsmithClient
      .newBuilder()
      .setDefaultFlagValueFunction(HelloController::defaultFlagHandler)
      .setApiKey(System.getenv("FLAGSMITH_API_KEY"))
      .build();

  @RequestMapping("/")
  public String index(
      @RequestParam("identifier") String identifier,
      @RequestParam("trait_key") String traitKey,
      @RequestParam("trait_value") String traitValue
  ) throws FlagsmithApiError, FlagsmithClientError {
    Map<String, String> traits = new HashMap<String, String>();
    if (StringUtils.isBlank(traitKey) && !StringUtils.isBlank(traitValue)) {
      traits.put(traitKey, traitValue);
    }

    Flags flags = flagsmith.getIdentityFlags(identifier, traits);

    String featureName = "secret_button";
    Boolean isFontColourEnabled = flags.isFeatureEnabled(featureName);

    String buttonValue = String.valueOf(flags.getFeatureValue(featureName));

    FontColor fontColor = parse(buttonValue, FontColor.class);

    return "Font colour is " + fontColor.getColour();
  }

  private static DefaultFlag defaultFlagHandler(String featureName) {
    DefaultFlag flag = new DefaultFlag();
    flag.setEnabled(Boolean.FALSE);

    if (featureName.equals("secret_button")) {
      flag.setValue("{\"colour\":\"#b8b8b8\"}");
    } else {
      flag.setValue(null);
    }

    return flag;
  }

  private <T> T parse(String data, Class<T> clazz) {
    if (null == mapper) {
      mapper = new ObjectMapper();
      mapper.configure(MapperFeature.USE_ANNOTATIONS, true);
      mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    }

    try {
      JsonNode json = mapper.readTree(data);
      return mapper.treeToValue(json, clazz);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  class FontColor {
    private String colour;

    public String getColour() {
      return colour;
    }

    public void setColour(String colour) {
      this.colour = colour;
    }
  }
}
