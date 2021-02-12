package com.flagsmith;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class HelloController {
	private static FlagsmithClient flagsmith = FlagsmithClient.newBuilder()
		.setApiKey("NowEDzKzNJXZVTVanLVdMQ")
		.build();

	@RequestMapping("/")
	public String index() {
		String fontSize = flagsmith.getFeatureFlagValue("font_size");

		return "Greetings from Spring Boot! Font size is " + fontSize;
	}
}