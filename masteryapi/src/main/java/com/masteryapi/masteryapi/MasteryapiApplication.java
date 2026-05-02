package com.masteryapi.masteryapi;

import com.masteryapi.masteryapi.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MasteryapiApplication {

	public static void main(String[] args) {
		DotenvLoader.load();
		SpringApplication.run(MasteryapiApplication.class, args);
	}

}
