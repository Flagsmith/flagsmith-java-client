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
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HelloController {
  private static volatile ObjectMapper mapper = null;

  private static FlagsmithClient flagsmith = FlagsmithClient
      .newBuilder()
      .setDefaultFlagValueFunction(HelloController::defaultFlagHandler)
      .setApiKey(System.getenv("FLAGSMITH_API_KEY"))
      .build();

  @GetMapping("")
  @ResponseBody
  public ModelAndView index(
      @RequestParam(name = "identifier", defaultValue = "") String identifier,
      @RequestParam(name = "trait_key", defaultValue = "") String traitKey,
      @RequestParam(name = "trait_value", defaultValue = "") String traitValue
  ) throws FlagsmithApiError, FlagsmithClientError {
    String featureName = "secret_button";
    Flags flags;

    if (!StringUtils.isBlank(identifier)) {
      Map<String, String> traits = new HashMap<String, String>();
      if (!StringUtils.isBlank(traitKey) && !StringUtils.isBlank(traitValue)) {
        traits.put(traitKey, traitValue);
      }
      flags = flagsmith.getIdentityFlags(identifier, traits);
    } else {
      flags = flagsmith.getEnvironmentFlags();
    }

    System.out.println("Flags: " + String.valueOf(flags.getAllFlags()));

    Boolean showButton = flags.isFeatureEnabled(featureName);

    System.out.println("showButton: " + String.valueOf(showButton));

    Object value = flags.getFeatureValue(featureName);

    System.out.println("value: " + String.valueOf(value));

    String buttonValue = value instanceof String ? (String) value : ((TextNode) value).textValue();

    FontColour fontColor = parse(buttonValue, FontColour.class);

    ModelAndView view = new ModelAndView();
    view.setViewName("index");
    view.addObject("show_button", showButton);
    view.addObject("font_colour", fontColor.getColour());
    return view;
  }

  private static DefaultFlag defaultFlagHandler(String featureName) {
    DefaultFlag flag = new DefaultFlag();
    flag.setEnabled(Boolean.FALSE);

    if (featureName.equals("secret_button")) {
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
}
