package com.flagsmith;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.flagsmith.exceptions.FlagsmithApiError;
import com.flagsmith.exceptions.FlagsmithClientError;
import com.flagsmith.models.DefaultFlag;
import com.flagsmith.models.Flags;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HelloController {
  private static volatile ObjectMapper mapper = null;

  private static FlagsmithClient flagsmith = FlagsmithClient
      .newBuilder()
      .setDefaultFlagValueFunction(HelloController::defaultFlagHandler)
      .setApiKey(System.getenv("FLAGSMITH_API_KEY"))
      .build();


  @GetMapping("")
  public String index() {
    return "index.html";
  }

  @GetMapping("/status")
  @ResponseBody
  public ButtonResponse index(
      @RequestParam("identifier") String identifier,
      @RequestParam("trait_key") String traitKey,
      @RequestParam("trait_value") String traitValue
  ) throws FlagsmithApiError, FlagsmithClientError {
    Map<String, String> traits = new HashMap<String, String>();
    if (!StringUtils.isBlank(traitKey) && !StringUtils.isBlank(traitValue)) {
      traits.put(traitKey, traitValue);
    }

    Flags flags = flagsmith.getIdentityFlags(identifier, traits);

    String featureName = "secret_button";
    Boolean isFontColourEnabled = flags.isFeatureEnabled(featureName);

    Object value = flags.getFeatureValue(featureName);

    String buttonValue = ((TextNode) flags.getFeatureValue(featureName)).textValue();

    FontColour fontColor = parse(buttonValue, FontColour.class);

    return new ButtonResponse(isFontColourEnabled, fontColor.getColour());
  }

  private static DefaultFlag defaultFlagHandler(String featureName) {
    DefaultFlag flag = new DefaultFlag();
    flag.setEnabled(Boolean.FALSE);

    if (featureName.equals("secret_button")) {
      ObjectNode text = getMapper().createObjectNode();
      text.put("colour", "#b8b8b8");
      flag.setValue("{\"colour\": \"#ababab\"}");
    } else {
      flag.setValue(null);
    }

    return flag;
  }

  private static ObjectMapper getMapper() {
    if (null == mapper) {
      mapper = new ObjectMapper();
      mapper.configure(MapperFeature.USE_ANNOTATIONS, true);
      mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    }

    return mapper;
  }

  private <T> T parse(String data, Class<T> clazz) {
    try {
      JsonNode json = getMapper().readTree(data);
      return getMapper().treeToValue(json, clazz);
    } catch (JsonProcessingException e) {
      return null;
    }
  }

  class ButtonResponse {
    @JsonProperty("show_button")
    private Boolean showButton;
    @JsonProperty("font_colour")
    private String fontColour;

    public ButtonResponse(Boolean showButton, String fontColour) {
      this.fontColour = fontColour;
      this.showButton = showButton;
    }

    public String getFontColour() {
      return fontColour;
    }

    public Boolean getshowButton() {
      return showButton;
    }
  }
}
